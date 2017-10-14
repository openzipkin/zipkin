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
package zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import java.util.List;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

final class TracedAsyncSpanConsumer implements AsyncSpanConsumer {
  private final Brave brave;
  private final AsyncSpanConsumer delegate;
  private final String component;

  TracedAsyncSpanConsumer(Brave brave, AsyncSpanConsumer delegate) {
    this.brave = brave;
    this.delegate = delegate;
    this.component = delegate.getClass().getSimpleName();
  }

  @Override public void accept(List<Span> spans, final Callback<Void> callback) {
    // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
    if (brave.serverSpanThreadBinder().getCurrentServerSpan() == null ||
        brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan() == null) {
      delegate.accept(spans, callback);
      return;
    }

    brave.localTracer().startNewSpan(component, "accept");
    delegate.accept(spans, new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
        brave.localTracer().finishSpan();
        callback.onSuccess(value);
      }

      @Override public void onError(Throwable t) {
        brave.localTracer().finishSpan();
        callback.onError(t);
      }
    });
  }
}
