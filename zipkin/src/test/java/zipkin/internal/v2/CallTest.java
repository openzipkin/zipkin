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
package zipkin.internal.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

public class CallTest {

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock Callback callback;

  @Test public void constant_execute() throws Exception {
    Call<String> call = Call.create("foo");

    assertThat(call.execute())
      .isEqualTo("foo");
  }

  @Test public void constant_submit() throws Exception {
    Call<String> call = Call.create("foo");

    call.enqueue(callback);

    verify(callback).onSuccess("foo");
  }

  @Test public void constant_execute_null() throws Exception {
    Call<Void> call = Call.create(null);

    assertThat(call.execute()).isNull();
  }

  @Test public void constant_submit_null() throws Exception {
    Call<Void> call = Call.create(null);

    call.enqueue(callback);

    verify(callback).onSuccess(isNull());
  }

  @Test public void constant_submit_cancel() throws Exception {
    Call<Void> call = Call.create(null);
    call.cancel();

    assertThat(call.isCanceled()).isTrue();

    call.enqueue(callback);

    verify(callback).onError(isA(IOException.class));
  }

  @Test public void executesOnce() throws Exception {
    Call<Void> call = Call.create(null);
    call.execute();

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {

    }

    try {
      call.enqueue(callback);
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {

    }
  }

  @Test public void enqueuesOnce() throws Exception {
    Call<Void> call = Call.create(null);
    call.enqueue(callback);

    try {
      call.enqueue(callback);
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {
    }

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {
    }
  }

  @Test(timeout = 1000L)
  public void concurrent_executesOrSubmitsOnce() throws InterruptedException {
    Call<Void> call = Call.create(null);

    int tryCount = 100;

    AtomicInteger executeOrSubmit = new AtomicInteger();

    callback = new Callback() {
      @Override public void onSuccess(@Nullable Object value) {
        executeOrSubmit.incrementAndGet();
      }

      @Override public void onError(Throwable t) {

      }
    };

    ExecutorService exec = Executors.newFixedThreadPool(10);
    List<Runnable> tries = new ArrayList<>(tryCount);
    for (int i = 0; i < tryCount; i++) {
      tries.add(i % 2 == 0 ? () -> {
        try {
          call.execute();
          executeOrSubmit.incrementAndGet();
        } catch (Exception e) {
        }
      } : () -> call.enqueue(callback));
    }

    tries.stream().forEach(exec::execute);
    exec.shutdown();
    exec.awaitTermination(1, TimeUnit.SECONDS);

    assertThat(executeOrSubmit.get()).isEqualTo(1);
  }
}
