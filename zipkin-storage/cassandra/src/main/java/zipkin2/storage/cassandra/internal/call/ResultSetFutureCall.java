/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import com.datastax.driver.core.exceptions.BusyConnectionException;
import com.datastax.driver.core.exceptions.BusyPoolException;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.datastax.driver.core.exceptions.QueryConsistencyException;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import zipkin2.Call;
import zipkin2.Callback;

/**
 * Future call pattern that takes advantage of special 'get' hooks on {@link
 * com.datastax.driver.core.ResultSetFuture}.
 */
// some copy/pasting is ok here as debugging is obscured when the type hierarchy gets deep.
public abstract class ResultSetFutureCall<V> extends Call.Base<V>
  implements Call.Mapper<ResultSet, V> {
  /** Defers I/O until {@link #enqueue(Callback)} or {@link #execute()} are called. */
  protected abstract ListenableFuture<ResultSet> newFuture();

  volatile ListenableFuture<ResultSet> future;

  @Override
  protected V doExecute() {
    return map(getUninterruptibly(future = newFuture()));
  }

  @Override
  protected void doEnqueue(Callback<V> callback) {
    // Similar to Futures.addCallback except doesn't double-wrap
    class CallbackListener implements Runnable {
      @Override
      public void run() {
        try {
          callback.onSuccess(map(getUninterruptibly(future)));
        } catch (Throwable t) {
          propagateIfFatal(t);
          callback.onError(t);
        }
      }
    }
    try {
      (future = newFuture()).addListener(new CallbackListener(), DirectExecutor.INSTANCE);
    } catch (Throwable t) {
      propagateIfFatal(t);
      callback.onError(t);
      throw t;
    }
  }

  @Override
  protected void doCancel() {
    ListenableFuture<ResultSet> maybeFuture = future;
    if (maybeFuture != null) maybeFuture.cancel(true);
  }

  @Override
  protected final boolean doIsCanceled() {
    ListenableFuture<ResultSet> maybeFuture = future;
    return maybeFuture != null && maybeFuture.isCancelled();
  }

  /** Sets {@link zipkin2.storage.StorageComponent#isOverCapacity(java.lang.Throwable)} */
  public static boolean isOverCapacity(Throwable e) {
    return e instanceof QueryConsistencyException ||
      e instanceof BusyConnectionException ||
      e instanceof BusyPoolException;
  }

  static ResultSet getUninterruptibly(ListenableFuture<ResultSet> future) {
    if (future instanceof ResultSetFuture) {
      return ((ResultSetFuture) future).getUninterruptibly();
    }

    // Like Guava's Uninterruptables.getUninterruptibly, except we process exceptions
    boolean interrupted = false;
    try {
      // loop on interrupted until get() returns or throws something else
      while (true) {
        try {
          return future.get();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } catch (ExecutionException e) {
      // emulate ResultSetFuture.getUninterruptibly unwrapping of driver exceptions
      Throwable cause = e.getCause();
      if (cause instanceof Error) throw ((Error) cause);
      if (cause instanceof DriverException) throw ((DriverException) cause).copy();
      throw new DriverInternalError("Unexpected exception thrown", cause);
    } finally {
      // Reset once, instead of doing so each get() was interrupted.
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
