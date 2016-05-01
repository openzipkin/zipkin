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
package zipkin.scribe;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;
import java.util.List;
import zipkin.AsyncSpanConsumer;
import zipkin.Callback;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.Span;
import zipkin.StorageComponent;
import zipkin.internal.Lazy;
import zipkin.spanstore.guava.GuavaSpanConsumer;

import static java.util.Collections.emptyList;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link GuavaSpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeCollector implements AutoCloseable {

  /** Configuration including defaults needed to receive spans from a Scribe category. */
  public static final class Builder {
    CollectorSampler sampler = CollectorSampler.ALWAYS_SAMPLE;
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String category = "zipkin";
    int port = 9410;

    /**
     * {@link CollectorSampler#isSampled(Span) samples spans} to reduce load on the storage system.
     * Defaults to always sample.
     */
    public Builder sampler(CollectorSampler sampler) {
      this.sampler = checkNotNull(sampler, "sampler");
      return this;
    }

    /** Aggregates and reports collection metrics to a monitoring system. Defaults to no-op. */
    public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("scribe");
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

    /**
     * @param storage Once spans are sampled, they are {@link AsyncSpanConsumer#accept(List,
     * Callback) queued for storage} using this component.
     */
    public ScribeCollector build(StorageComponent storage) {
      checkNotNull(storage, "storage");
      return new ScribeCollector(this, new Lazy<AsyncSpanConsumer>() {
        @Override protected AsyncSpanConsumer compute() {
          AsyncSpanConsumer result = storage.asyncSpanConsumer(sampler, metrics);
          return checkNotNull(result, storage + ".asyncSpanConsumer()");
        }
      });
    }
  }

  final ThriftServer server;

  ScribeCollector(Builder builder, Lazy<AsyncSpanConsumer> consumer) {
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(builder.category, consumer, builder.metrics);
    ThriftServiceProcessor processor =
        new ThriftServiceProcessor(new ThriftCodecManager(), emptyList(), scribe);
    server = new ThriftServer(processor, new ThriftServerConfig().setPort(builder.port)).start();
  }

  @Override
  public void close() {
    server.close();
  }
}
