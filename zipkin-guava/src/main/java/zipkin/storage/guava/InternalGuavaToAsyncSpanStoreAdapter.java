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

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

import static com.google.common.util.concurrent.Futures.addCallback;
import static zipkin.internal.Util.checkNotNull;

final class InternalGuavaToAsyncSpanStoreAdapter implements AsyncSpanStore {
  final GuavaSpanStore delegate;

  InternalGuavaToAsyncSpanStoreAdapter(GuavaSpanStore delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
  }

  @Override public void getTraces(QueryRequest request, Callback<List<List<Span>>> callback) {
    addCallback(delegate.getTraces(request), new InternalForwardingCallback<>(callback));
  }

  @Override public void getTrace(long id, Callback<List<Span>> callback) {
    getTrace(0L, id, callback);
  }

  @Override public void getTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    addCallback(delegate.getTrace(traceIdHigh, traceIdLow),
        new InternalForwardingCallback<>(callback));
  }

  @Override public void getRawTrace(long traceId, Callback<List<Span>> callback) {
    getRawTrace(0L, traceId, callback);
  }

  @Override
  public void getRawTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    addCallback(delegate.getRawTrace(traceIdHigh, traceIdLow),
        new InternalForwardingCallback<>(callback));
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    addCallback(delegate.getServiceNames(), new InternalForwardingCallback<>(callback));
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    addCallback(delegate.getSpanNames(serviceName), new InternalForwardingCallback<>(callback));
  }

  @Override public void getDependencies(long endTs, @Nullable Long lookback,
      Callback<List<DependencyLink>> callback) {
    addCallback(delegate.getDependencies(endTs, lookback), new InternalForwardingCallback<>(callback));
  }

  @Override public String toString() {
    return delegate.toString();
  }
}
