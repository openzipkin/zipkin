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
package zipkin.storage.cassandra3;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.Nullable;

abstract class ListenableFutureCall<V> extends Call<V> {
  volatile boolean canceled;
  boolean executed;
  volatile ListenableFuture<V> future;

  protected ListenableFutureCall() {
  }

  @Override public final V execute() throws IOException {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }

    if (isCanceled()) throw new IOException("Canceled");
    return Futures.getUnchecked(future = newFuture());
  }

  @Override public final void enqueue(Callback<V> callback) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }

    if (isCanceled()) {
      callback.onError(new IOException("Canceled"));
    } else {
      Futures.addCallback((future = newFuture()), new FutureCallback<V>() {
        @Override public void onSuccess(@Nullable V result) {
          callback.onSuccess(result);
        }

        @Override public void onFailure(Throwable t) {
          callback.onError(t);
        }
      });
    }
  }

  protected abstract ListenableFuture<V> newFuture();

  @Override public final void cancel() {
    canceled = true;
    ListenableFuture<V> maybeFuture = future;
    if (maybeFuture != null) maybeFuture.cancel(true);
  }

  @Override public final boolean isCanceled() {
    if (canceled) return true;
    ListenableFuture<V> maybeFuture = future;
    return maybeFuture != null && maybeFuture.isCancelled();
  }
  @Override public Call<V> clone() {
    throw new UnsupportedOperationException("one-shot deal");
  }
}
