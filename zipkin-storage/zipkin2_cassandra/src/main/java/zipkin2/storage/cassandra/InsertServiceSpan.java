/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.cassandra;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_SPANS;

final class InsertServiceSpan extends ResultSetFutureCall {

  @AutoValue static abstract class Input {
    abstract String service();

    abstract String span();

    Input() {
    }
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final ConcurrentMap<Input, InsertServiceSpan> cache;

    Factory(Session session, long ttl) {
      this.session = session;
      this.preparedStatement = session.prepare(QueryBuilder.insertInto(TABLE_SERVICE_SPANS)
        .value("service", QueryBuilder.bindMarker("service"))
        .value("span", QueryBuilder.bindMarker("span"))
      );
      this.cache = CacheBuilder.newBuilder()
        .expireAfterWrite(ttl, TimeUnit.MILLISECONDS)
        .ticker(new Ticker() {
          @Override public long read() {
            return nanoTime();
          }
        })
        // TODO: maximum size or weight
        .<Input, InsertServiceSpan>build().asMap();
    }

    // visible for testing, since nanoTime is weird and can return negative
    long nanoTime() {
      return System.nanoTime();
    }

    Input newInput(String service, String span) {
      return new AutoValue_InsertServiceSpan_Input(service, span);
    }

    Call<ResultSet> create(Input input) {
      if (input == null) throw new NullPointerException("input == null");
      if (cache.containsKey(input)) return Call.create(null);
      InsertServiceSpan realCall = new InsertServiceSpan(this, input);
      if (cache.putIfAbsent(input, realCall) != null) return Call.create(null);
      return realCall;
    }
  }

  final Factory factory;
  final Input input;

  InsertServiceSpan(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service", input.service())
      .setString("span", input.span()));
  }

  @Override protected ResultSet doExecute() throws IOException {
    try {
      return super.doExecute();
    } catch (IOException | RuntimeException | Error e) {
      factory.cache.remove(input, InsertServiceSpan.this); // invalidate
      throw e;
    }
  }

  @Override protected void doEnqueue(Callback<ResultSet> callback) {
    super.doEnqueue(new Callback<ResultSet>() {
      @Override public void onSuccess(ResultSet value) {
        callback.onSuccess(value);
      }

      @Override public void onError(Throwable t) {
        factory.cache.remove(input, InsertServiceSpan.this); // invalidate
        callback.onError(t);
      }
    });
  }

  @Override protected void doCancel() {
    factory.cache.remove(input, InsertServiceSpan.this); // invalidate
    super.doCancel();
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertServiceSpan");
  }

  @Override public InsertServiceSpan clone() {
    return new InsertServiceSpan(factory, input);
  }
}
