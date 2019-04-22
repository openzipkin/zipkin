/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.internal.call;

import java.io.IOException;
import java.util.List;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.DelayLimiter;

public abstract class DeduplicatingVoidCallFactory<I> {
  final DelayLimiter<I> delayLimiter;

  protected DeduplicatingVoidCallFactory(int ttl, int cardinality) {
    delayLimiter = DelayLimiter.newBuilder().ttl(ttl).cardinality(cardinality).build();
  }

  protected abstract Call<Void> newCall(I input);

  public final void maybeAdd(I input, List<Call<Void>> calls) {
    if (input == null) throw new NullPointerException("input == null");
    if (!delayLimiter.shouldInvoke(input)) return;
    calls.add(new InvalidatingVoidCall<>(newCall(input), delayLimiter, input));
  }

  public final void clear() {
    delayLimiter.clear();
  }

  public static final class InvalidatingVoidCall<I> extends Call.Base<Void> {
    final Call<Void> delegate;
    final DelayLimiter<I> delayLimiter;
    final I input;

    InvalidatingVoidCall(Call<Void> delegate, DelayLimiter<I> delayLimiter, I input) {
      this.delegate = delegate;
      this.delayLimiter = delayLimiter;
      this.input = input;
    }

    @Override public Call<Void> clone() {
      return new InvalidatingVoidCall<>(delegate.clone(), delayLimiter, input);
    }

    @Override protected Void doExecute() throws IOException {
      try {
        return delegate.execute();
      } catch (IOException | RuntimeException | Error e) {
        delayLimiter.invalidate(input);
        throw e;
      }
    }

    @Override protected void doEnqueue(Callback<Void> callback) {
      delegate.enqueue(new Callback<Void>() {
        @Override public void onSuccess(Void value) {
          callback.onSuccess(value);
        }

        @Override public void onError(Throwable t) {
          delayLimiter.invalidate(input);
          callback.onError(t);
        }
      });
    }

    @Override public void doCancel() {
      delayLimiter.invalidate(input);
      delegate.cancel();
    }
  }
}
