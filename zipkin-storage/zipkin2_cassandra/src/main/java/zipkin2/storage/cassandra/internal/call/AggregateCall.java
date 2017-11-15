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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.Nullable;

public abstract class AggregateCall<I, O> extends Call.Base<O> {
  final Logger log = Logger.getLogger(getClass().getName());
  final List<Call<I>> calls;

  protected AggregateCall(List<Call<I>> calls) {
    assert !calls.isEmpty() : "do not create single-element aggregates";
    this.calls = calls;
  }

  protected abstract O newOutput();

  protected abstract void append(I input, O output);

  protected abstract boolean isEmpty(O output);

  @Override protected O doExecute() throws IOException {
    final CountDownLatch countDown = new CountDownLatch(1);
    final AtomicReference<Object> result = new AtomicReference<>();

    doEnqueue(new Callback<O>() {
      @Override public void onSuccess(O value) {
        result.set(value);
        countDown.countDown();
      }

      @Override public void onError(Throwable t) {
        result.set(t);
        countDown.countDown();
      }

      @Override public String toString() {
        return "AwaitSuccessOrError";
      }
    });

    boolean interrupted = false;
    try {
      while (true) {
        try {
          countDown.await();
          Object value = result.get();
          if (value instanceof Throwable) {
            if (value instanceof Error) throw (Error) value;
            if (value instanceof RuntimeException) throw (RuntimeException) value;
            if (value instanceof IOException) throw (IOException) value;
            // Don't set interrupted status when the callback received InterruptedException
            throw new RuntimeException((Throwable) value);
          }
          return (O) value;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override protected void doEnqueue(Callback<O> callback) {
    int length = calls.size();
    AtomicInteger remaining = new AtomicInteger(length);
    O result = newOutput();
    for (int i = 0; i < length; i++) {
      Call<I> call = calls.get(i);
      call.enqueue(new CountdownCallback(call, remaining, result, callback));
    }
  }

  @Override protected void doCancel() {
    for (int i = 0, length = calls.size(); i < length; i++) {
      calls.get(i).cancel();
    }
  }

  class CountdownCallback implements Callback<I> {
    final Call<I> call;
    final AtomicInteger remaining;
    @Nullable final O result;
    final Callback<O> callback;

    CountdownCallback(Call<I> call, AtomicInteger remaining, O result, Callback<O> callback) {
      this.call = call;
      this.remaining = remaining;
      this.result = result;
      this.callback = callback;
    }

    @Override public void onSuccess(I value) {
      synchronized (callback) {
        try {
          append(value, result);
        } finally {
          if (remaining.decrementAndGet() == 0) {
            callback.onSuccess(result);
          }
        }
      }
    }

    @Override public synchronized void onError(Throwable throwable) {
      if (log.isLoggable(Level.INFO)) {
        log.log(Level.INFO, "error from " + call, throwable);
      }
      if (remaining.decrementAndGet() > 0) return;
      synchronized (callback) {
        if (isEmpty(result)) {
          callback.onError(throwable);
        } else {
          callback.onSuccess(result);
        }
      }
    }
  }

  protected List<Call<I>> cloneCalls() {
    int length = calls.size();
    if (length == 1) return Collections.singletonList(calls.get(0).clone());
    List<Call<I>> result = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      result.add(calls.get(i).clone());
    }
    return result;
  }
}
