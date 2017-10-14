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
package zipkin.storage;

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CallbackCaptor;
import zipkin.internal.Nullable;

final class InternalAsyncToBlockingSpanStoreAdapter implements SpanStore {
  final AsyncSpanStore delegate;

  InternalAsyncToBlockingSpanStoreAdapter(AsyncSpanStore delegate) {
    this.delegate = delegate;
  }

  @Override public List<List<Span>> getTraces(QueryRequest request) {
    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    delegate.getTraces(request, captor);
    return captor.get();
  }

  @Override @Deprecated public List<Span> getTrace(long id) {
   return getTrace(0L, id);
  }

  @Override public List<Span> getTrace(long traceIdHigh, long traceIdLow) {
    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    delegate.getTrace(traceIdHigh, traceIdLow, captor);
    return captor.get();
  }

  @Override @Deprecated public List<Span> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public List<Span> getRawTrace(long traceIdHigh, long traceIdLow) {
    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    delegate.getRawTrace(traceIdHigh, traceIdLow, captor);
    return captor.get();
  }

  @Override public List<String> getServiceNames() {
    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    delegate.getServiceNames(captor);
    return captor.get();
  }

  @Override public List<String> getSpanNames(String serviceName) {
    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    delegate.getSpanNames(serviceName, captor);
    return captor.get();
  }

  @Override public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    delegate.getDependencies(endTs, lookback, captor);
    return captor.get();
  }

  @Override public String toString() {
    return delegate.toString();
  }
}
