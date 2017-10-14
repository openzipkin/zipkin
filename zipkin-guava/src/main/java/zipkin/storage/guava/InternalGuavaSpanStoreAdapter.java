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
package zipkin.storage.guava;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

import static zipkin.internal.Util.checkNotNull;

/**
 * A {@link GuavaSpanStore} derived from an {@link AsyncSpanStore}. Used by callers who prefer to
 * compose futures.
 */
final class InternalGuavaSpanStoreAdapter implements GuavaSpanStore {
  final AsyncSpanStore delegate;

  InternalGuavaSpanStoreAdapter(AsyncSpanStore delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
  }

  @Override public ListenableFuture<List<List<Span>>> getTraces(QueryRequest request) {
    CallbackListenableFuture<List<List<Span>>> result = new CallbackListenableFuture<>();
    delegate.getTraces(request, result);
    return result;
  }

  @Override @Deprecated public ListenableFuture<List<Span>> getTrace(long id) {
    return getTrace(0L, id);
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceIdHigh, long traceIdLow) {
    CallbackListenableFuture<List<Span>> result = new CallbackListenableFuture<>();
    delegate.getTrace(traceIdHigh, traceIdLow, result);
    return result;
  }

  @Override @Deprecated public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceIdHigh, long traceIdLow) {
    CallbackListenableFuture<List<Span>> result = new CallbackListenableFuture<>();
    delegate.getRawTrace(traceIdHigh, traceIdLow, result);
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

  @Override public String toString() {
    return delegate.toString();
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
