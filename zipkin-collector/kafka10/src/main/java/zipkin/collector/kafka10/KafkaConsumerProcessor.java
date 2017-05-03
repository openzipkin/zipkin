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
package zipkin.collector.kafka10;

import java.util.Collections;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Codec;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;

import static zipkin.storage.Callback.NOOP;

/** Consumes spans from Kafka messages, ignoring malformed input */
final class KafkaConsumerProcessor implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerProcessor.class);

  final Consumer<byte[], byte[]> kafkaConsumer;
  final Collector collector;
  final CollectorMetrics metrics;

  KafkaConsumerProcessor(Consumer<byte[], byte[]> kafkaConsumer, Collector collector,
      CollectorMetrics metrics) {
    this.kafkaConsumer = kafkaConsumer;
    this.collector = collector;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    try {
      LOG.info("Kafka consumer starting polling loop.");
      while (!Thread.interrupted()) {
        final ConsumerRecords<byte[], byte[]> consumerRecords = kafkaConsumer.poll(1000);
        LOG.debug("Kafka polling returned batch of {} messages.", consumerRecords.count());
        consumerRecords.forEach((cr) -> {
          metrics.incrementMessages();
          final byte[] bytes = cr.value();

          if (bytes.length == 0) {
            metrics.incrementMessagesDropped();
          } else {
            // In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
            // .. If the first byte isn't in that range, it isn't a thrift.
            //
            // When byte(0) == '[' (91), assume it is a list of json-encoded spans
            //
            // When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
            // .. When serializing a Span (Struct), the first byte will be the type of a field
            // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
            // .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
            if (bytes[0] == '[') {
              collector.acceptSpans(bytes, Codec.JSON, NOOP);
            } else {
              if (bytes[0] == 12 /* TType.STRUCT */) {
                collector.acceptSpans(bytes, Codec.THRIFT, NOOP);
              } else {
                collector.acceptSpans(Collections.singletonList(bytes), Codec.THRIFT, NOOP);
              }
            }
          }
        });
      }
    } catch (InterruptException e) {
      // shutdown was initiated
    } finally {
      LOG.info("Kafka consumer polling loop stopped.");
      LOG.info("Closing Kafka consumer...");
      // todo attempting to close the consumer when the thread is in interrupted status goes nowhere
      // todo this clears that status, but is that a legit thing to do if a thread has been interrupted
      // todo (clear the status and continue some cleanup)?
      Thread.interrupted();
      kafkaConsumer.close();
      LOG.info("Kafka consumer closed.");
    }
  }
}
