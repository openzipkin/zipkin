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
package zipkin.server.internal.brave;

import brave.Tracer;
import brave.Tracing;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

final class TracingSpanStore implements SpanStore {
  private final Tracer tracer;
  private final SpanStore delegate;

  TracingSpanStore(Tracing tracing, StorageComponent component) {
    this.tracer = tracing.tracer();
    this.delegate = component.spanStore();
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request) {
    brave.Span span = tracer.nextSpan().name("get-traces").tag("request", request.toString());

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getTraces(request);
    } finally {
      span.finish();
    }
  }

  @Override
  public List<Span> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public List<Span> getTrace(long traceIdHigh, long traceIdLow) {
    brave.Span span = tracer.nextSpan().name("get-trace");

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getTrace(traceIdHigh, traceIdLow);
    } finally {
      span.finish();
    }
  }

  @Override
  public List<Span> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public List<Span> getRawTrace(long traceIdHigh, long traceIdLow) {
    brave.Span span = tracer.nextSpan().name("get-raw-trace");

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getRawTrace(traceIdHigh, traceIdLow);
    } finally {
      span.finish();
    }
  }

  @Override
  public List<String> getServiceNames() {
    brave.Span span = tracer.nextSpan().name("get-service-names");

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getServiceNames();
    } finally {
      span.finish();
    }
  }

  @Override
  public List<String> getSpanNames(String serviceName) {
    brave.Span span = tracer.nextSpan().name("get-span-names");

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getSpanNames(serviceName);
    } finally {
      span.finish();
    }
  }

  @Override
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    brave.Span span = tracer.nextSpan().name("get-dependencies");

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
      return delegate.getDependencies(endTs, lookback);
    } finally {
      span.finish();
    }
  }
}
