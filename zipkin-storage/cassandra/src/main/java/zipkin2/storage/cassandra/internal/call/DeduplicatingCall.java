/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.ResultSet;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.DelayLimiter;

public abstract class DeduplicatingCall<I> extends ResultSetFutureCall {

  public abstract static class Factory<I, C extends DeduplicatingCall<I>> {
    final DelayLimiter<I> delayLimiter;

    protected Factory(long cacheTtl) {
      delayLimiter = DelayLimiter.newBuilder()
        .expireAfter(cacheTtl, TimeUnit.MILLISECONDS)
        .maximumSize(1000L).build();
    }

    protected abstract C newCall(I input);

    public final Call<ResultSet> create(I input) {
      if (input == null) throw new NullPointerException("input == null");
      if (!delayLimiter.shouldInvoke(input)) return Call.create(null);
      return newCall(input);
    }

    public final void clear() {
      delayLimiter.clear();
    }
  }

  final Factory<I, ?> factory;
  final I input;

  protected DeduplicatingCall(Factory<I, ?> factory, I input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSet doExecute() throws IOException {
    try {
      return super.doExecute();
    } catch (IOException | RuntimeException | Error e) {
      factory.delayLimiter.invalidate(input);
      throw e;
    }
  }

  @Override protected void doEnqueue(Callback<ResultSet> callback) {
    super.doEnqueue(
      new Callback<ResultSet>() {
        @Override public void onSuccess(ResultSet value) {
          callback.onSuccess(value);
        }

        @Override public void onError(Throwable t) {
          factory.delayLimiter.invalidate(input);
          callback.onError(t);
        }
      });
  }

  @Override protected void doCancel() {
    factory.delayLimiter.invalidate(input);
    super.doCancel();
  }
}
