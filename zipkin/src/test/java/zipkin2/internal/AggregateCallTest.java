/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.DependencyLink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregateCallTest {

  @Mock Call<Void> call1, call2;
  @Mock Callback<Void> callback;

  @Test void newVoidCall_emptyNotAllowed() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      AggregateCall.newVoidCall(List.of());
    });
  }

  @Test void newVoidCall_singletonReturnsOnlyElement() {
    assertThat(AggregateCall.newVoidCall(List.of(call1)))
      .isEqualTo(call1);
  }

  @Test void newVoidCall_joinsMultipleCalls() {
    assertThat(AggregateCall.newVoidCall(List.of(call1, call2)))
      .isInstanceOf(AggregateCall.AggregateVoidCall.class)
      .extracting("delegate")
      .isEqualTo(List.of(call1, call2));
  }

  @Test void execute() throws Exception {
    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));

    assertThat(call.execute())
      .isNull();

    verify(call1).execute();
    verify(call2).execute();
    verifyNoMoreInteractions(call1, call2);
  }

  @Test void enqueue() {
    successCallback(call1);
    successCallback(call2);

    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));

    call.enqueue(callback);

    verify(callback).onSuccess(null);

    verify(call1).enqueue(any(Callback.class));
    verify(call2).enqueue(any(Callback.class));
    verifyNoMoreInteractions(call1, call2);
  }

  @Test void enqueue_cancel() {
    call1 = Call.create(null);
    call2 = Call.create(null);

    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));
    call.cancel();

    assertThat(call.isCanceled()).isTrue();
    assertThat(call1.isCanceled()).isTrue();
    assertThat(call2.isCanceled()).isTrue();

    call.enqueue(callback);

    verify(callback).onError(isA(IOException.class));
  }

  @Test void executesOnce() throws Exception {
    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));
    call.execute();

    assertThatThrownBy(call::execute)
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test void enqueuesOnce() {
    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));
    call.enqueue(callback);

    assertThatThrownBy(() -> call.enqueue(callback))
      .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(call::execute)
      .isInstanceOf(IllegalStateException.class);
  }

  @Test void execute_errorDoesntStopOtherCalls() throws Exception {
    Exception e = new IllegalArgumentException();
    when(call1.execute()).thenThrow(e);
    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(e.getClass());
    } catch (IllegalArgumentException ex) {
    }

    verify(call1).execute();
    verify(call2).execute();
    verifyNoMoreInteractions(call1, call2);
  }

  /** This shows that regardless of success or error, we block on completion */
  @Test @Timeout(1000L) void enqueue_blocksOnCompletion() throws InterruptedException {
    CountDownLatch callsLatch = new CountDownLatch(10);
    ExecutorService exec = Executors.newFixedThreadPool(10);

    class LatchCall extends Call.Base<Void> {
      final boolean fail;

      LatchCall(boolean fail) {
        this.fail = fail;
      }

      @Override protected Void doExecute() {
        throw new UnsupportedOperationException();
      }

      @Override protected void doEnqueue(Callback<Void> callback) {
        exec.submit(() -> {
          try {
            callsLatch.await();
          } catch (InterruptedException e) {
            callback.onError(e);
          }
          if (fail) {
            callback.onError(new IOException());
          } else {
            callback.onSuccess(null);
          }
        });
      }

      @Override public Call<Void> clone() {
        return new LatchCall(fail);
      }
    }

    List<Call<Void>> calls = new ArrayList<>();
    for (int i = 0; i < 10; i++) calls.add(new LatchCall(i % 2 == 0));
    Call<Void> call = AggregateCall.newVoidCall(calls);

    AtomicReference<Object> result = new AtomicReference<>();
    call.enqueue(new Callback<Void>() {
      @Override public void onSuccess(Void value) {
        result.set("foo");
      }

      @Override public void onError(Throwable t) {
        result.set(t);
      }
    });

    assertThat(result).hasValue(null);
    callsLatch.countDown(); // one down
    assertThat(result).hasValue(null);
    callsLatch.countDown(); // two down
    assertThat(result).hasValue(null);
    for (int i = 0; i < 8; i++) callsLatch.countDown();

    // wait for threads to finish
    exec.shutdown();
    exec.awaitTermination(1, TimeUnit.SECONDS);

    assertThat(result.get()).isNotNull();
  }

  @Test void enqueue_errorDoesntStopOtherCalls() {
    Exception e = new IllegalArgumentException();
    errorCallback(call1, e);
    successCallback(call2);

    Call<Void> call = AggregateCall.newVoidCall(List.of(call1, call2));

    call.enqueue(callback);

    verify(callback).onError(e);

    verify(call1).enqueue(any(Callback.class));
    verify(call2).enqueue(any(Callback.class));
    verifyNoMoreInteractions(call1, call2);
  }

  static void successCallback(Call<Void> call) {
    doAnswer(a -> {
      ((Callback<Void>) a.getArgument(0)).onSuccess(null);
      return a;
    }).when(call).enqueue(any(Callback.class));
  }

  static void errorCallback(Call<Void> call, Exception e) {
    doAnswer(a -> {
      ((Callback<Void>) a.getArgument(0)).onError(e);
      return a;
    }).when(call).enqueue(any(Callback.class));
  }

  @Test void execute_finish() throws Exception {
    Call<List<DependencyLink>> call = new AggregateDependencyLinks(List.of(
      Call.create(List.of(DependencyLink.newBuilder().parent("a").child("b").callCount(1).build())),
      Call.create(List.of(DependencyLink.newBuilder().parent("a").child("b").callCount(3).build()))
    ));

    assertThat(call.execute())
      .containsExactly(DependencyLink.newBuilder().parent("a").child("b").callCount(4).build());
  }

  @Test void enqueue_finish() {
    Call<List<DependencyLink>> call = new AggregateDependencyLinks(List.of(
      Call.create(List.of(DependencyLink.newBuilder().parent("a").child("b").callCount(1).build())),
      Call.create(List.of(DependencyLink.newBuilder().parent("a").child("b").callCount(3).build()))
    ));

    Callback<List<DependencyLink>> callback = mock(Callback.class);

    call.enqueue(callback);

    verify(callback)
      .onSuccess(List.of(DependencyLink.newBuilder().parent("a").child("b").callCount(4).build()));
  }

  static final class AggregateDependencyLinks
    extends AggregateCall<List<DependencyLink>, List<DependencyLink>> {
    AggregateDependencyLinks(List<Call<List<DependencyLink>>> calls) {
      super(calls);
    }

    @Override protected List<DependencyLink> newOutput() {
      return new ArrayList<>();
    }

    @Override protected void append(List<DependencyLink> input, List<DependencyLink> output) {
      output.addAll(input);
    }

    // this is the part we are testing, that we can peform a finish step
    @Override protected List<DependencyLink> finish(List<DependencyLink> done) {
      return DependencyLinker.merge(done);
    }

    @Override public AggregateDependencyLinks clone() {
      return new AggregateDependencyLinks(cloneCalls());
    }
  }
}
