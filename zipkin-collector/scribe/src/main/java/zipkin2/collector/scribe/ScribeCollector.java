/*
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
package zipkin2.collector.scribe;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.filter.SpanFilter;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link SpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeCollector extends CollectorComponent {

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Configuration including defaults needed to receive spans from a Scribe category. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(ScribeCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String category = "zipkin";
    int port = 9410;

    @Override
    public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override
    public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("scribe");
      delegate.metrics(this.metrics);
      return this;
    }

    @Override
    public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    @Override
    public CollectorComponent.Builder filters(List<SpanFilter> filters) {
      delegate.filters(filters);
      return this;
    }

    /** Category zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder category(String category) {
      if (category == null) throw new NullPointerException("category == null");
      this.category = category;
      return this;
    }

    /** The port to listen on. Defaults to 9410 */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    @Override
    public ScribeCollector build() {
      return new ScribeCollector(this);
    }
  }

  final ThriftServer server;

  ScribeCollector(Builder builder) {
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(builder);
    ThriftServiceProcessor processor =
        new ThriftServiceProcessor(new ThriftCodecManager(), emptyList(), scribe);
    server = new ThriftServer(processor, new ThriftServerConfig().setPort(builder.port));
  }

  /** Will throw an exception if the {@link Builder#port(int) port} is already in use. */
  @Override
  public ScribeCollector start() {
    server.start();
    return this;
  }

  @Override
  public CheckResult check() {
    try {
      checkState(server.isRunning(), "server not running");
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override
  public void close() {
    server.close();
  }
}
