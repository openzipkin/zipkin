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
package zipkin.server.brave;

import com.github.kristofa.brave.LocalTracer;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;

final class TracedSpanStore implements SpanStore {
  private final LocalTracer tracer;
  private final SpanStore delegate;
  private final String component;

  TracedSpanStore(LocalTracer tracer, SpanStore delegate) {
    this.tracer = tracer;
    this.delegate = delegate;
    this.component = delegate.getClass().getSimpleName();
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
  public List<Span> getTrace(long traceId) {
    tracer.startNewSpan(component, "get-trace");
    try {
      return delegate.getTrace(traceId);
    } finally {
      tracer.finishSpan();
    }
  }

  @Override
  public List<Span> getRawTrace(long traceId) {
    tracer.startNewSpan(component, "get-spans-by-trace-id");
    try {
      return delegate.getRawTrace(traceId);
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
