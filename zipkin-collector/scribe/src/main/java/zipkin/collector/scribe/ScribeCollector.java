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
package zipkin.collector.scribe;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;
import zipkin.collector.Collector;
import zipkin.collector.CollectorComponent;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.storage.StorageComponent;
import zipkin.storage.guava.GuavaSpanConsumer;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link GuavaSpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeCollector implements CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to receive spans from a Scribe category. */
  public static final class Builder implements CollectorComponent.Builder {
    Collector.Builder delegate = Collector.builder(ScribeCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String category = "zipkin";
    int port = 9410;

    @Override public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("scribe");
      delegate.metrics(this.metrics);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    /** Category zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder category(String category) {
      this.category = checkNotNull(category, "category");
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
  @Override public ScribeCollector start() {
    server.start();
    return this;
  }

  @Override public CheckResult check() {
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
