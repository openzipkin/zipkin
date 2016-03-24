/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.spanstore.guava;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.async.AsyncSpanStore;
import zipkin.async.Callback;
import zipkin.internal.Nullable;

/**
 * A {@link GuavaSpanStore} derived from an {@link AsyncSpanStore}. Used by callers who prefer to
 * compose futures.
 */
public final class GuavaSpanStoreAdapter implements GuavaSpanStore {

  public static GuavaSpanStore create(AsyncSpanStore delegate) {
    return delegate instanceof GuavaSpanStore
        ? (GuavaSpanStore) delegate : new GuavaSpanStoreAdapter(delegate);
  }

  private final AsyncSpanStore delegate;

  GuavaSpanStoreAdapter(AsyncSpanStore delegate) {
    this.delegate = delegate;
  }

  @Override public ListenableFuture<Void> accept(List<Span> spans) {
    CallbackListenableFuture<Void> result = new CallbackListenableFuture<>();
    delegate.accept(spans, result);
    return result;
  }

  @Override public ListenableFuture<List<List<Span>>> getTraces(QueryRequest request) {
    CallbackListenableFuture<List<List<Span>>> result = new CallbackListenableFuture<>();
    delegate.getTraces(request, result);
    return result;
  }

  @Override public ListenableFuture<List<Span>> getTrace(long id) {
    CallbackListenableFuture<List<Span>> result = new CallbackListenableFuture<>();
    delegate.getTrace(id, result);
    return result;
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    CallbackListenableFuture<List<Span>> result = new CallbackListenableFuture<>();
    delegate.getRawTrace(traceId, result);
    return result;
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    CallbackListenableFuture<List<String>> result = new CallbackListenableFuture<>();
    delegate.getServiceNames(result);
    return result;
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    CallbackListenableFuture<List<String>> result = new CallbackListenableFuture<>();
    delegate.getSpanNames(serviceName, result);
    return result;
  }

  @Override public ListenableFuture<List<DependencyLink>> getDependencies(long endTs,
      @Nullable Long lookback) {
    CallbackListenableFuture<List<DependencyLink>> result = new CallbackListenableFuture<>();
    delegate.getDependencies(endTs, lookback, result);
    return result;
  }

  static final class CallbackListenableFuture<V> extends AbstractFuture<V> implements Callback<V> {
    @Override public void onSuccess(@Nullable V value) {
      set(value);
    }

    @Override public void onError(Throwable t) {
      setException(t);
    }
  }
}
