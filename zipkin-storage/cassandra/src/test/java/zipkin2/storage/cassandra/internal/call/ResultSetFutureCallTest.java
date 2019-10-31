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
import com.datastax.driver.core.exceptions.BusyConnectionException;
import com.datastax.driver.core.exceptions.BusyPoolException;
import com.datastax.driver.core.exceptions.QueryConsistencyException;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import org.junit.Test;
import zipkin2.Callback;

import static com.google.common.util.concurrent.JdkFutureAdapters.listenInPoolThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ResultSetFutureCallTest {
  Future<ResultSet> future = mock(Future.class);
  ResultSet resultSet = mock(ResultSet.class);

  ResultSetFutureCall<ResultSet> call =
    mock(ResultSetFutureCall.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
  Callback<ResultSet> callback = mock(Callback.class);

  @Test public void submit_cancel_beforeCreateFuture() {
    call.cancel();

    assertThat(call.isCanceled()).isTrue();
  }

  @Test public void submit_callsFutureGet() throws Exception {
    when(call.newFuture()).thenReturn(listenInPoolThread(future));
    when(call.map(resultSet)).thenReturn(resultSet);

    when(future.isDone()).thenReturn(true);
    when(future.get()).thenReturn(resultSet);

    call.enqueue(callback);

    verify(future).isDone();
    verify(future).get();

    verify(callback).onSuccess(resultSet);
    verifyNoMoreInteractions(future, callback);
  }

  @Test public void submit_cancel_afterEnqueue() {
    when(call.newFuture()).thenReturn(listenInPoolThread(future));
    call.enqueue(callback);
    call.cancel();

    assertThat(call.isCanceled()).isTrue();
    verify(future).cancel(true);
  }

  @Test public void submit_callbackError_onErrorCreatingFuture() {
    when(call.newFuture()).thenThrow(new IllegalArgumentException());

    // ensure exception is re-thrown
    try {
      call.enqueue(callback);
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
    }

    // ensure the callback received the exception
    verify(callback).onError(any(IllegalArgumentException.class));
    verifyNoMoreInteractions(callback);
  }

  // below are load related exceptions which should result in a backoff of storage requests
  @Test public void isOverCapacity() {
    InetSocketAddress sa = InetSocketAddress.createUnresolved("host", 9402);

    assertThat(ResultSetFutureCall.isOverCapacity(new BusyPoolException(() -> sa, 100))).isTrue();
    assertThat(ResultSetFutureCall.isOverCapacity(new BusyConnectionException(() -> sa))).isTrue();
    assertThat(ResultSetFutureCall.isOverCapacity(mock(QueryConsistencyException.class))).isTrue();

    // not applicable
    assertThat(ResultSetFutureCall.isOverCapacity(
      new IllegalStateException("Rejected execution"))).isFalse();
  }
}
