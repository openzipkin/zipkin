/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server.brave;

import brave.Tracer;
import brave.Tracing;
import java.util.List;
import zipkin.Constants;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

final class TracingAsyncSpanConsumer implements AsyncSpanConsumer {
  private final Tracer tracer;
  private final AsyncSpanConsumer delegate;

  TracingAsyncSpanConsumer(Tracing tracing, AsyncSpanConsumer delegate) {
    this.tracer = tracing.tracer();
    this.delegate = delegate;
  }

  @Override public void accept(List<Span> spans, final Callback<Void> callback) {
    // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
    if (tracer.currentSpan() == null) {
      delegate.accept(spans, callback);
      return;
    }
    brave.Span span = tracer.nextSpan().name("accept");
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      delegate.accept(spans, new Callback<Void>() {
        @Override public void onSuccess(@Nullable Void value) {
          span.finish();
          callback.onSuccess(value);
        }

        @Override public void onError(Throwable t) {
          tagError(t, span);
          span.finish();
          callback.onError(t);
        }
      });
    } catch (RuntimeException | Error e) {
      tagError(e, span);
      span.finish();
      throw e;
    }
  }

  static void tagError(Throwable t, brave.Span span) {
    String message = t.getMessage();
    span.tag(Constants.ERROR, message != null ? message : t.getClass().getSimpleName());
  }
}
