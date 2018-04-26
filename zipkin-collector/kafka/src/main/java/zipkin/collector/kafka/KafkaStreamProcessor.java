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
package zipkin.collector.kafka;

import java.util.Collections;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;

import static zipkin.SpanDecoder.DETECTING_DECODER;
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

      if (bytes.length < 2) { // need two bytes to check if protobuf
        metrics.incrementMessagesDropped();
        continue;
      }

      // If we received legacy single-span encoding, decode it into a singleton list
      if (!protobuf3(bytes) && bytes[0] <= 16 && bytes[0] != 12 /* thrift, but not a list */) {
        try {
          metrics.incrementBytes(bytes.length);
          Span span = SpanDecoder.THRIFT_DECODER.readSpan(bytes);
          collector.accept(Collections.singletonList(span), NOOP);
        } catch (RuntimeException e) {
          metrics.incrementMessagesDropped();
        }
      } else {
        collector.acceptSpans(bytes, DETECTING_DECODER, NOOP);
      }
    }
  }

  /* span key or trace ID key */
  static boolean protobuf3(byte[] bytes) {
    return bytes[0] == 10 && bytes[1] != 0; // varint follows and won't be zero
  }
}
