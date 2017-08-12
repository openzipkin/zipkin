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
package zipkin.collector.kafka;

import java.util.Collections;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;

import static zipkin.SpanDecoder.DETECTING_DECODER;
import static zipkin.SpanDecoder.THRIFT_DECODER;
import static zipkin.storage.Callback.NOOP;

/** Consumes spans from Kafka messages, ignoring malformed input */
final class KafkaStreamProcessor implements Runnable {
  final KafkaStream<byte[], byte[]> stream;
  final Collector collector;
  final CollectorMetrics metrics;

  KafkaStreamProcessor(
      KafkaStream<byte[], byte[]> stream, Collector collector, CollectorMetrics metrics) {
    this.stream = stream;
    this.collector = collector;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    ConsumerIterator<byte[], byte[]> messages = stream.iterator();
    while (messages.hasNext()) {
      byte[] bytes = messages.next().message();
      metrics.incrementMessages();

      if (bytes.length == 0) {
        metrics.incrementMessagesDropped();
        continue;
      }

      if (bytes[0] == '[' /* json list */ || bytes[0] == 12 /* thrift list */) {
        collector.acceptSpans(bytes, DETECTING_DECODER, NOOP);
      } else { // assume legacy single-span encoding
        collector.acceptSpans(Collections.singletonList(bytes), THRIFT_DECODER, NOOP);
      }
    }
  }
}
