/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import com.datastax.oss.driver.api.core.RequestThrottlingException;
import com.datastax.oss.driver.api.core.connection.BusyConnectionException;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.servererrors.QueryConsistencyException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import zipkin2.Call;
import zipkin2.Callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ResultSetFutureCallTest {
  CompletableFuture<AsyncResultSet> future = new CompletableFuture<>();
  AsyncResultSet resultSet = mock(AsyncResultSet.class);

  ResultSetFutureCall<AsyncResultSet> call = new ResultSetFutureCall<>() {
    @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
      return ResultSetFutureCallTest.this.future;
    }

    @Override public Call<AsyncResultSet> clone() {
      return null;
    }

    @Override public AsyncResultSet map(AsyncResultSet input) {
      return input;
    }
  };

  static final class CompletableCallback<T> extends CompletableFuture<T> implements Callback<T> {
    @Override public void onSuccess(T value) {
      complete(value);
    }

    @Override public void onError(Throwable t) {
      completeExceptionally(t);
    }
  }

  CompletableCallback<AsyncResultSet> callback = new CompletableCallback<>();

  @Test void enqueue_cancel_beforeCreateFuture() {
    call.cancel();

    assertThat(call.isCanceled()).isTrue();
  }

  @Test void enqueue_callsFutureGet() throws Exception {
    call.enqueue(callback);

    future.complete(resultSet);

    assertThat(callback.get()).isEqualTo(resultSet);
  }

  @Test void enqueue_cancel_afterEnqueue() {
    call.enqueue(callback);
    call.cancel();

    assertThat(call.isCanceled()).isTrue();
    // this.future will be wrapped, so can't check if that is canceled.
    assertThat(call.future.isCancelled()).isTrue();
  }

  @Test void enqueue_callbackError_onErrorCreatingFuture() {
    IllegalArgumentException error = new IllegalArgumentException();
    call = new ResultSetFutureCall<>() {
      @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
        throw error;
      }

      @Override public Call<AsyncResultSet> clone() {
        return null;
      }

      @Override public AsyncResultSet map(AsyncResultSet input) {
        return input;
      }
    };

    call.enqueue(callback);

    // ensure the callback received the exception
    assertThat(callback.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(callback::get).hasCause(error);
  }

  // below are load related exceptions which should result in a backoff of storage requests
  @Test void isOverCapacity() {
    assertThat(ResultSetFutureCall.isOverCapacity(
      new RequestThrottlingException("The session is shutting down"))).isTrue();
    assertThat(ResultSetFutureCall.isOverCapacity(new BusyConnectionException(100))).isTrue();
    assertThat(ResultSetFutureCall.isOverCapacity(mock(QueryConsistencyException.class))).isTrue();

    // not applicable
    assertThat(ResultSetFutureCall.isOverCapacity(
      new IllegalStateException("Rejected execution"))).isFalse();
  }
}
