/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.internal.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallTest {

  @Mock Callback callback;

  @Test void constant_execute() throws Exception {
    Call<String> call = Call.create("foo");

    assertThat(call.execute())
      .isEqualTo("foo");
  }

  @Test void constant_submit() {
    Call<String> call = Call.create("foo");

    call.enqueue(callback);

    verify(callback).onSuccess("foo");
  }

  @Test void constant_execute_null() throws Exception {
    Call<Void> call = Call.create(null);

    assertThat(call.execute()).isNull();
  }

  @Test void constant_submit_null() {
    Call<Void> call = Call.create(null);

    call.enqueue(callback);

    verify(callback).onSuccess(isNull());
  }

  @Test void constant_submit_cancel() {
    Call<Void> call = Call.create(null);
    call.cancel();

    assertThat(call.isCanceled()).isTrue();

    call.enqueue(callback);

    verify(callback).onError(isA(IOException.class));
  }

  @Test void executesOnce() throws Exception {
    Call<Void> call = Call.create(null);
    call.execute();

    assertThatThrownBy(() -> call.execute())
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test void enqueuesOnce() {
    Call<Void> call = Call.create(null);
    call.enqueue(callback);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> call.execute())
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Timeout(1000L)
  void concurrent_executesOrSubmitsOnce() throws InterruptedException {
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

    tries.forEach(exec::execute);
    exec.shutdown();
    exec.awaitTermination(1, TimeUnit.SECONDS);

    assertThat(executeOrSubmit.get()).isEqualTo(1);
  }

  @Test void constantEqualsConstant() {
    assertThat(Call.create(null))
      .isEqualTo(Call.create(null));
  }

  @Test void emptyList() throws Exception {
    Call<List<String>> call = Call.emptyList();

    assertThat(call.execute()).isEmpty();
  }

  @Test void emptyList_independentInstances() {
    assertThat(Call.emptyList())
      .isNotSameAs(Call.emptyList());
  }

  @Test void map_execute() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> {
      assertThat(foo).isEqualTo("foo");
      return "bar";
    });

    assertThat(fooBarCall.execute())
      .isEqualTo("bar");
  }

  @Test void map_enqueue() {
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> "bar");

    fooBarCall.enqueue(callback);

    verify(callback).onSuccess("bar");
  }

  @Test void map_enqueue_mappingException() {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.map(foo -> {
      throw error;
    });

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test void flatMap_execute() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> {
      assertThat(foo).isEqualTo("foo");
      return barCall;
    });

    assertThat(fooBarCall.execute())
      .isEqualTo("bar");
  }

  @Test void flatMap_enqueue() {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> barCall);

    fooBarCall.enqueue(callback);

    verify(callback).onSuccess("bar");
  }

  @Test void flatMap_enqueue_mappingException() {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> fooBarCall = fooCall.flatMap(foo -> {
      assertThat(foo).isEqualTo("foo");
      throw error;
    });

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test void flatMap_enqueue_callException() {
    IllegalArgumentException error = new IllegalArgumentException();
    Call<String> fooCall = Call.create("foo");
    Call<String> exceptionCall = errorCall(error);

    Call<String> fooBarCall = fooCall.flatMap(foo -> exceptionCall);

    fooBarCall.enqueue(callback);

    verify(callback).onError(error);
  }

  @Test void flatMap_cancelPropagates() throws Exception {
    Call<String> fooCall = Call.create("foo");
    Call<String> barCall = Call.create("bar");
    Call<String> fooBarCall = fooCall.flatMap(foo -> barCall);

    fooBarCall.execute(); // to instantiate the chain.
    fooBarCall.cancel();

    assertThat(fooBarCall.isCanceled()).isTrue();
    assertThat(fooCall.isCanceled()).isTrue();
    assertThat(barCall.isCanceled()).isTrue();
  }

  @Test void onErrorReturn_execute_onError() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      IllegalArgumentException exception = new IllegalArgumentException();
      Call<String> errorCall = errorCall(exception);

      Call<String> resolvedCall = errorCall.handleError(
        (error, callback) -> callback.onError(error)
      );

      resolvedCall.execute();
    });
  }

  @Test void onErrorReturn_execute_onSuccess() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess("foo")
    );

    assertThat(resolvedCall.execute())
      .isEqualTo("foo");
  }

  @Test void onErrorReturn_execute_onSuccess_null() throws Exception {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess(null)
    );

    assertThat(resolvedCall.execute())
      .isNull();
  }

  @Test void onErrorReturn_enqueue_onError() {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onError(error)
    );

    resolvedCall.enqueue(callback);

    verify(callback).onError(exception);
  }

  @Test void onErrorReturn_enqueue_onSuccess() {
    IllegalArgumentException exception = new IllegalArgumentException();
    Call<String> errorCall = errorCall(exception);

    Call<String> resolvedCall = errorCall.handleError(
      (error, callback) -> callback.onSuccess("foo")
    );

    resolvedCall.enqueue(callback);

    verify(callback).onSuccess("foo");
  }

  @Test void onErrorReturn_enqueue_onSuccess_null() {
    NoSuchElementException exception = new NoSuchElementException();
    Call<List<String>> call = errorCall(exception);

    Call<List<String>> resolvedCall = call.handleError((error, callback) -> {
        if (error instanceof NoSuchElementException) {
          callback.onSuccess(List.of());
        } else {
          callback.onError(error);
        }
      }
    );

    resolvedCall.enqueue(callback);

    verify(callback).onSuccess(List.of());
  }

  static <T> Call<T> errorCall(RuntimeException error) {
    return new Call.Base<T>() {
      @Override protected T doExecute() {
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
