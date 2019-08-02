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

import brave.Tracer;
import brave.Tracing;
import java.io.IOException;
import java.util.List;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ForwardingStorageComponent;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

// public for use in ZipkinServerConfiguration
public final class TracingStorageComponent extends ForwardingStorageComponent {
  final Tracing tracing;
  final StorageComponent delegate;

  public TracingStorageComponent(Tracing tracing, StorageComponent delegate) {
    this.tracing = tracing;
    this.delegate = delegate;
  }

  @Override protected StorageComponent delegate() {
    return delegate;
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return new TracingServiceAndSpanNames(tracing, delegate.serviceAndSpanNames());
  }

  @Override public SpanStore spanStore() {
    return new TracingSpanStore(tracing, delegate.spanStore());
  }

  @Override public AutocompleteTags autocompleteTags() {
    return new TracingAutocompleteTags(tracing, delegate.autocompleteTags());
  }

  @Override public SpanConsumer spanConsumer() {
    return new TracingSpanConsumer(tracing, delegate.spanConsumer());
  }

  @Override public CheckResult check() {
    return delegate.check();
  }

  @Override public void close() throws IOException {
    delegate.close();
  }

  @Override public String toString() {
    return "Traced{" + delegate + "}";
  }

  static final class TracingSpanStore implements SpanStore {
    final Tracer tracer;
    final SpanStore delegate;

    TracingSpanStore(Tracing tracing, SpanStore delegate) {
      this.tracer = tracing.tracer();
      this.delegate = delegate;
    }

    @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
      return new TracedCall<>(tracer, delegate.getTraces(request), "get-traces");
    }

    @Override public Call<List<Span>> getTrace(String traceId) {
      return new TracedCall<>(tracer, delegate.getTrace(traceId), "get-trace");
    }

    @Override @Deprecated public Call<List<String>> getServiceNames() {
      return new TracedCall<>(tracer, delegate.getServiceNames(), "get-service-names");
    }

    @Override @Deprecated public Call<List<String>> getSpanNames(String serviceName) {
      return new TracedCall<>(tracer, delegate.getSpanNames(serviceName), "get-span-names");
    }

    @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
      return new TracedCall<>(
        tracer, delegate.getDependencies(endTs, lookback), "get-dependencies");
    }

    @Override public String toString() {
      return "Traced{" + delegate + "}";
    }
  }

  static final class TracingAutocompleteTags implements AutocompleteTags {
    final Tracer tracer;
    final AutocompleteTags delegate;

    TracingAutocompleteTags(Tracing tracing, AutocompleteTags delegate) {
      this.tracer = tracing.tracer();
      this.delegate = delegate;
    }

    @Override public Call<List<String>> getKeys() {
      return new TracedCall<>(tracer, delegate.getKeys(), "get-keys");
    }

    @Override public Call<List<String>> getValues(String key) {
      return new TracedCall<>(tracer, delegate.getValues(key), "get-values");
    }

    @Override public String toString() {
      return "Traced{" + delegate + "}";
    }
  }

  static final class TracingServiceAndSpanNames implements ServiceAndSpanNames {
    final Tracer tracer;
    final ServiceAndSpanNames delegate;

    TracingServiceAndSpanNames(Tracing tracing, ServiceAndSpanNames delegate) {
      this.tracer = tracing.tracer();
      this.delegate = delegate;
    }

    @Override public Call<List<String>> getServiceNames() {
      return new TracedCall<>(tracer, delegate.getServiceNames(), "get-service-names");
    }

    @Override public Call<List<String>> getRemoteServiceNames(String serviceName) {
      return new TracedCall<>(tracer, delegate.getRemoteServiceNames(serviceName),
        "get-remote-service-names");
    }

    @Override public Call<List<String>> getSpanNames(String serviceName) {
      return new TracedCall<>(tracer, delegate.getSpanNames(serviceName), "get-span-names");
    }

    @Override public String toString() {
      return "Traced{" + delegate + "}";
    }
  }

  static final class TracingSpanConsumer implements SpanConsumer {
    final Tracer tracer;
    final SpanConsumer delegate;

    TracingSpanConsumer(Tracing tracing, SpanConsumer delegate) {
      this.tracer = tracing.tracer();
      this.delegate = delegate;
    }

    @Override public Call<Void> accept(List<Span> spans) {
      return new TracedCall<>(tracer, delegate.accept(spans), "accept-spans");
    }

    @Override public String toString() {
      return "Traced{" + delegate + "}";
    }
  }
}
