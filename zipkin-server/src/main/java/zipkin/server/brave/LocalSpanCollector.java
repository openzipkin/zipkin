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

import com.github.kristofa.brave.AbstractSpanCollector;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.SpanCodec;
import java.util.List;
import zipkin.Codec;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.StorageComponent;
import zipkin.internal.SpanConsumerLogger;

/**
 * A Brave {@link SpanCollector} that forwards to the local {@link SpanStore}.
 */
public class LocalSpanCollector extends AbstractSpanCollector {
  private final StorageComponent storage;
  private final CollectorSampler sampler;
  private final CollectorMetrics metrics;
  private final SpanConsumerLogger logger;

  public LocalSpanCollector(StorageComponent storage, int flushInterval,
      CollectorSampler sampler, CollectorMetrics metrics) {
    super(SpanCodec.THRIFT, new EmptySpanCollectorMetricsHandler(), checkPositive(flushInterval));
    this.storage = storage;
    this.sampler = sampler;
    this.metrics = metrics.forTransport("local");
    this.logger = new SpanConsumerLogger(LocalSpanCollector.class, this.metrics);
  }

  private static int checkPositive(int flushInterval) {
    if (flushInterval <= 0) {
      throw new IllegalArgumentException("flushInterval must be a positive duration in seconds");
    }
    return flushInterval;
  }

  @Override
  protected void sendSpans(byte[] thrift) {
    logger.acceptedMessage();
    logger.readBytes(thrift.length);
    List<Span> spans = null;
    try {
      spans = Codec.THRIFT.readSpans(thrift);
      logger.readSpans(spans.size());
      storage.asyncSpanConsumer(sampler, metrics).accept(spans, logger.acceptSpansCallback(spans));
    } catch (RuntimeException e) {
      if (spans != null) logger.errorAcceptingSpans(spans, e);
    }
  }
}
