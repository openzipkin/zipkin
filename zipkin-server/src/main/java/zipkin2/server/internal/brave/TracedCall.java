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
package zipkin2.server.internal.brave;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracer;
import java.io.IOException;
import zipkin2.Call;
import zipkin2.Callback;

public final class TracedCall<V> extends Call<V> {
  final Tracer tracer;
  final Call<V> delegate;
  final String name;

  public TracedCall(Tracer tracer, Call<V> delegate, String name) {
    this.tracer = tracer;
    this.delegate = delegate;
    this.name = name;
  }

  @Override public V execute() throws IOException {
    ScopedSpan span = tracer.startScopedSpan(name);
    try {
      return delegate.execute();
    } catch (RuntimeException | IOException | Error e) {
      span.error(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override public void enqueue(Callback<V> callback) {
    Span span = tracer.nextSpan().name(name).start();
    try {
      if (span.isNoop()) {
        delegate.enqueue(callback);
      } else {
        delegate.enqueue(new SpanFinishingCallback<>(callback, span));
      }
    } catch (RuntimeException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    }
  }

  @Override public void cancel() {
    delegate.cancel();
  }

  @Override public boolean isCanceled() {
    return delegate.isCanceled();
  }

  @Override public Call<V> clone() {
    return new TracedCall<>(tracer, delegate, name);
  }

  @Override public String toString() {
    return "Traced(" + delegate + ")";
  }

  static final class SpanFinishingCallback<V> implements Callback<V> {
    private final Callback<V> delegate;
    private final Span span;

    SpanFinishingCallback(Callback<V> delegate, Span span) {
      this.delegate = delegate;
      this.span = span;
    }

    @Override public void onSuccess(V value) {
      delegate.onSuccess(value);
      span.finish();
    }

    @Override public void onError(Throwable t) {
      delegate.onError(t);
      span.error(t).finish();
    }

    @Override public String toString() {
      return "Traced(" + delegate + ")";
    }
  }
}
