/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector;

import java.util.Collection;
import java.util.List;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.storage.SpanConsumer;

/**
 * Instrumented applications report spans over a transport such as Kafka. Zipkin collectors receive
 * these messages, {@link SpanBytesDecoder#decode(byte[], Collection) decoding them into spans},
 * {@link CollectorSampler#isSampled(String, boolean) apply sampling}, and {@link
 * SpanConsumer#accept(List) queues them for storage}.
 *
 * <p>Callbacks on this type are invoked by zipkin collectors to improve the visibility of the
 * system. A typical implementation will report metrics to a telemetry system for analysis and
 * reporting.
 *
 * <h3>Spans Collected vs Queryable Spans</h3>
 *
 * <p>A span queried may be comprised of multiple spans collected. While instrumentation should
 * report complete spans, Instrumentation often patch the same span twice, ex adding annotations.
 * Also, RPC spans include at least 2 messages due to the client and the server reporting
 * separately. Finally, some storage components merge patches at ingest. For these reasons, you
 * should be cautious to alert on queryable spans vs stored spans, unless you control the
 * instrumentation in such a way that queryable spans/message is reliable.
 *
 * <h3>Key Relationships</h3>
 *
 * <p>The following relationships can be used to consider health of the tracing system.
 *
 * <pre>
 * <ul>
 * <li>Successful Messages = {@link #incrementMessages() Accepted messages} -
 * {@link #incrementMessagesDropped() Dropped messages}. Alert when this is less than amount of
 * messages sent from instrumentation.</li>
 * <li>Stored spans &lt;= {@link #incrementSpans(int) Accepted spans} - {@link
 * #incrementSpansDropped(int) Dropped spans}. Alert when this drops below the
 * {@link CollectorSampler#isSampled(long, boolean) collection-tier sample rate}.
 * </li>
 * </ul>
 * </pre>
 */
public interface CollectorMetrics {

  /**
   * Those who wish to partition metrics by transport can call this method to include the transport
   * type in the backend metric key.
   *
   * <p>For example, an implementation may by default report {@link #incrementSpans(int) incremented
   * spans} to the key "zipkin.collector.span.accepted". When {@code metrics.forTransport("kafka"}
   * is called, the counter would report to "zipkin.collector.kafka.span.accepted"
   *
   * @param transportType ex "http", "rabbitmq", "kafka"
   */
  CollectorMetrics forTransport(String transportType);

  /**
   * Increments count of messages received, which contain 0 or more spans. Ex POST requests or Kafka
   * messages consumed.
   */
  void incrementMessages();

  /**
   * Increments count of messages that could not be read. Ex malformed content, or peer disconnect.
   */
  void incrementMessagesDropped();

  /**
   * Increments the count of spans read from a successful message. When bundling is used, accepted
   * spans will be a larger number than successful messages.
   */
  void incrementSpans(int quantity);

  /**
   * Increments the number of bytes containing serialized spans in a message.
   *
   * <p>Note: this count should relate to the raw data structures, like json or thrift, and discount
   * compression, enveloping, etc.
   */
  void incrementBytes(int quantity);

  /**
   * Increments the count of spans dropped for any reason. For example, failure queueing to storage
   * or sampling decisions.
   */
  void incrementSpansDropped(int quantity);

  CollectorMetrics NOOP_METRICS =
      new CollectorMetrics() {

        @Override
        public CollectorMetrics forTransport(String transportType) {
          return this;
        }

        @Override
        public void incrementMessages() {}

        @Override
        public void incrementMessagesDropped() {}

        @Override
        public void incrementSpans(int quantity) {}

        @Override
        public void incrementBytes(int quantity) {}

        @Override
        public void incrementSpansDropped(int quantity) {}

        @Override
        public String toString() {
          return "NoOpCollectorMetrics";
        }
      };
}
