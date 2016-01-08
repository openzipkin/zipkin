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
package io.zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import io.zipkin.DependencyLink;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import java.util.Iterator;
import java.util.List;

public final class TraceWritesSpanStore implements SpanStore {
  private final LocalTracer tracer;
  private final SpanStore delegate;
  private final String component;

  public TraceWritesSpanStore(Brave brave, SpanStore delegate) {
    this.tracer = brave.localTracer();
    this.delegate = delegate;
    this.component = delegate.getClass().getSimpleName();
  }

  @Override
  public void accept(Iterator<Span> spans) {
    delegate.accept(spans); // don't trace writes
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request) {
    if (tracer.startNewSpan(component, "get-traces") != null) {
      tracer.submitBinaryAnnotation("request", request.toString());
    }
    try {
      return delegate.getTraces(request);
    } finally {
      tracer.finishSpan();
    }
  }

  @Override
  public List<List<Span>> getTracesByIds(List<Long> traceIds) {
    tracer.startNewSpan(component, "get-traces-by-ids");
    try {
      return delegate.getTracesByIds(traceIds);
    } finally {
      tracer.finishSpan();
    }
  }

  @Override
  public List<String> getServiceNames() {
    tracer.startNewSpan(component, "get-service-names");
    try {
      return delegate.getServiceNames();
    } finally {
      tracer.finishSpan();
    }
  }

  @Override
  public List<String> getSpanNames(String serviceName) {
    tracer.startNewSpan(component, "get-span-names");
    try {
      return delegate.getSpanNames(serviceName);
    } finally {
      tracer.finishSpan();
    }
  }

  @Override
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    tracer.startNewSpan(component, "get-dependencies");
    try {
      return delegate.getDependencies(endTs, lookback);
    } finally {
      tracer.finishSpan();
    }
  }
}
