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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import zipkin2.Call;
import zipkin2.Callback;

/**
 * Similar to {@link ListenableFutureCall} except it takes advantage of special 'get' hooks on
 * {@link com.datastax.driver.core.ResultSetFuture}.
 */
// some copy/pasting is ok here as debugging is obscured when the type hierarchy gets deep.
public abstract class ResultSetFutureCall extends Call.Base<ResultSet> {
  /** Defers I/O until {@link #enqueue(Callback)} or {@link #execute()} are called. */
  protected abstract ListenableFuture<ResultSet> newFuture();

  volatile ListenableFuture<ResultSet> future;

  @Override protected ResultSet doExecute() throws IOException {
    return getUninterruptibly(future = newFuture());
  }

  @Override protected void doEnqueue(Callback<ResultSet> callback) {
    // Similar to Futures.addCallback except doesn't double-wrap
    class CallbackListener implements Runnable {
      @Override public void run() {
        try {
          callback.onSuccess(getUninterruptibly(future));
        } catch (RuntimeException | Error e) {
          propagateIfFatal(e);
          callback.onError(e);
        }
      }
    }
    (future = newFuture()).addListener(new CallbackListener(), DirectExecutor.INSTANCE);
  }

  @Override protected void doCancel() {
    ListenableFuture<ResultSet> maybeFuture = future;
    if (maybeFuture != null) maybeFuture.cancel(true);
  }

  @Override protected final boolean doIsCanceled() {
    ListenableFuture<ResultSet> maybeFuture = future;
    return maybeFuture != null && maybeFuture.isCancelled();
  }

  static ResultSet getUninterruptibly(ListenableFuture<ResultSet> future) {
    if (future instanceof ResultSetFuture) {
      return ((ResultSetFuture) future).getUninterruptibly();
    }
    try { // emulate ResultSetFuture.getUninterruptibly
      return Uninterruptibles.getUninterruptibly(future);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Error) throw ((Error) cause);
      if (cause instanceof DriverException) throw ((DriverException) cause).copy();
      throw new DriverInternalError("Unexpected exception thrown", cause);
    }
  }
}
