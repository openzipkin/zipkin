/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.ResultSet;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

abstract class DeduplicatingCall<I> extends ResultSetFutureCall {

  abstract static class Factory<I, C extends DeduplicatingCall<I>> extends Ticker {
    final ConcurrentMap<I, C> cache;

    Factory(long cacheTtl) {
      // TODO: maximum size or weight
      cache =
          CacheBuilder.newBuilder()
              .expireAfterWrite(cacheTtl, TimeUnit.MILLISECONDS)
              .ticker(this)
              .<I, C>build()
              .asMap();
    }

    // visible for testing, since nanoTime is weird and can return negative
    @Override
    public long read() {
      return System.nanoTime();
    }

    abstract C newCall(I input);

    Call<ResultSet> create(I input) {
      if (input == null) throw new NullPointerException("input == null");
      if (cache.containsKey(input)) return Call.create(null);
      C realCall = newCall(input);
      if (cache.putIfAbsent(input, realCall) != null) return Call.create(null);
      return realCall;
    }
  }

  final Factory factory;
  final I input;

  DeduplicatingCall(Factory factory, I input) {
    this.factory = factory;
    this.input = input;
  }

  @Override
  protected ResultSet doExecute() throws IOException {
    try {
      return super.doExecute();
    } catch (IOException | RuntimeException | Error e) {
      factory.cache.remove(input, DeduplicatingCall.this); // invalidate
      throw e;
    }
  }

  @Override
  protected void doEnqueue(Callback<ResultSet> callback) {
    super.doEnqueue(
        new Callback<ResultSet>() {
          @Override
          public void onSuccess(ResultSet value) {
            callback.onSuccess(value);
          }

          @Override
          public void onError(Throwable t) {
            factory.cache.remove(input, DeduplicatingCall.this); // invalidate
            callback.onError(t);
          }
        });
  }

  @Override
  protected void doCancel() {
    factory.cache.remove(input, DeduplicatingCall.this); // invalidate
    super.doCancel();
  }
}
