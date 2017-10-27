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
package zipkin2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin2.internal.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.isNull;
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

    assertThatThrownBy(() -> call.execute())
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test public void enqueuesOnce() throws Exception {
    Call<Void> call = Call.create(null);
    call.enqueue(callback);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> call.execute())
      .isInstanceOf(IllegalStateException.class);
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

  @Test public void constantEqualsConstant() throws Exception {
    assertThat(Call.create(null))
      .isEqualTo(Call.create(null));
  }

  @Test public void emptyList() throws Exception {
    Call<List<String>> call = Call.emptyList();

    assertThat(call.execute()).isEmpty();
  }

  @Test public void emptyList_independentInstances() throws Exception {
    assertThat(Call.emptyList())
      .isNotSameAs(Call.emptyList());
  }

  @Test public void map_execute() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> {
      assertThat(foo).isEqualTo("foo");
      return "bar";
    });

    assertThat(fooBarCall.execute())
      .isEqualTo("bar");
  }

  @Test public void map_enqueue() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> "bar");

    fooBarCall.enqueue(callback);

    verify(callback).onSuccess("bar");
  }

  @Test public void map_enqueue_mappingException() throws Exception {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> {
      throw error;
    });

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test public void flatMap_execute() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> {
      assertThat(foo).isEqualTo("foo");
      return barCall;
    });

    assertThat(fooBarCall.execute())
      .isEqualTo("bar");
  }

  @Test public void flatMap_enqueue() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> barCall);

    fooBarCall.enqueue(callback);

    verify(callback).onSuccess("bar");
  }

  @Test public void flatMap_enqueue_mappingException() throws Exception {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.flatMap(foo -> {
      assertThat(foo).isEqualTo("foo");
      throw error;
    });

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test public void flatMap_enqueue_callException() throws Exception {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> exceptionCall = errorCall(error);

    Call<String> fooBarCall = fooCall.flatMap(foo -> exceptionCall);

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test public void flatMap_cancelPropagates() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> barCall);

    fooBarCall.execute(); // to instantiate the chain.
    fooBarCall.cancel();

    assertThat(fooBarCall.isCanceled()).isTrue();
    assertThat(fooCall.isCanceled()).isTrue();
    assertThat(barCall.isCanceled()).isTrue();
  }

  @Test(expected = IllegalArgumentException.class)
  public void onErrorReturn_execute_onError() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onError(error)
    );

    resolvedCall.execute();
  }

  @Test public void onErrorReturn_execute_onSuccess() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess("foo")
    );

    assertThat(resolvedCall.execute())
      .isEqualTo("foo");
  }

  @Test public void onErrorReturn_execute_onSuccess_null() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess(null)
    );

    assertThat(resolvedCall.execute())
      .isNull();
  }

  @Test public void onErrorReturn_enqueue_onError() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onError(error)
    );

    resolvedCall.enqueue(callback);

    verify(callback).onError(exception);
  }

  @Test public void onErrorReturn_enqueue_onSuccess() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess("foo")
    );

    resolvedCall.enqueue(callback);

    verify(callback).onSuccess("foo");
  }

  @Test public void onErrorReturn_enqueue_onSuccess_null() throws Exception {
    NoSuchElementException exception = new NoSuchElementException();
    Call<List<String>> call = errorCall(exception);

    Call<List<String>> resolvedCall = call.handleError((error, callback) -> {
        if (error instanceof NoSuchElementException) {
          callback.onSuccess(Collections.emptyList());
        } else {
          callback.onError(error);
        }
      }
    );

    resolvedCall.enqueue(callback);

    verify(callback).onSuccess(Collections.emptyList());
  }

  static <T> Call<T> errorCall(RuntimeException error) {
    return new Call.Base<T>() {
      @Override protected T doExecute() throws IOException {
        throw error;
      }

      @Override protected void doEnqueue(Callback<T> callback) {
        callback.onError(error);
      }

      @Override public Call<T> clone() {
        throw new AssertionError();
      }
    };
  }
}
