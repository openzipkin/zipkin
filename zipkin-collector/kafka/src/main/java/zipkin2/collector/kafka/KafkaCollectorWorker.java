/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.kafka;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

/** Consumes spans from Kafka messages, ignoring malformed input */
final class KafkaCollectorWorker implements Runnable {
  static final Logger LOG = LoggerFactory.getLogger(KafkaCollectorWorker.class);
  static final Callback<Void> NOOP =
      new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {}

        @Override
        public void onError(Throwable t) {}
      };

  final Properties properties;
  final List<String> topics;
  final Collector collector;
  final CollectorMetrics metrics;
  // added for integration tests only, see ITKafkaCollector
  final AtomicReference<List<TopicPartition>> assignedPartitions =
      new AtomicReference<>(List.of());
  final AtomicBoolean running = new AtomicBoolean(true);

  KafkaCollectorWorker(KafkaCollector.Builder builder) {
    properties = builder.properties;
    topics = Arrays.asList(builder.topic.split(","));
    collector = builder.delegate.build();
    metrics = builder.metrics;
  }

  @Override
  public void run() {
    try (KafkaConsumer<byte[], byte[]> kafkaConsumer = new KafkaConsumer<>(properties)) {
      kafkaConsumer.subscribe(
        topics,
        // added for integration tests only, see ITKafkaCollector
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            // technically we should remove only the revoked partitions but for test purposes it
            // does not matter
            assignedPartitions.set(List.of());
          }

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            assignedPartitions.set(List.copyOf(partitions));
          }
        });
      LOG.debug("Kafka consumer starting polling loop.");
      while (running.get()) {
        final ConsumerRecords<byte[], byte[]> consumerRecords = kafkaConsumer.poll(Duration.of(1000, ChronoUnit.MILLIS));
        LOG.debug("Kafka polling returned batch of {} messages.", consumerRecords.count());
        for (ConsumerRecord<byte[], byte[]> record : consumerRecords) {
          final byte[] bytes = record.value();
          metrics.incrementMessages();
          metrics.incrementBytes(bytes.length);

          if (bytes.length == 0) continue; // lenient on empty messages

          if (bytes.length < 2) { // need two bytes to check if protobuf
            metrics.incrementMessagesDropped();
          } else {
            // If we received legacy single-span encoding, decode it into a singleton list
            if (!protobuf3(bytes) && bytes[0] <= 16 && bytes[0] != 12 /* thrift, but not list */) {
              Span span;
              try {
                span = SpanBytesDecoder.THRIFT.decodeOne(bytes);
              } catch (RuntimeException e) {
                metrics.incrementMessagesDropped();
                continue;
              }
              collector.accept(List.of(span), NOOP);
            } else {
              collector.acceptSpans(bytes, NOOP);
            }
          }
        }
      }
    } catch (RuntimeException | Error e) {
      LOG.warn("Unexpected error in polling loop spans", e);
      throw e;
    } finally {
      LOG.debug("Kafka consumer polling loop stopped. Kafka consumer closed.");
    }
  }

  /**
   * Stop the polling loop
   */
  public void stop() {
    running.set(false);
  }

  /* span key or trace ID key */
  static boolean protobuf3(byte[] bytes) {
    return bytes[0] == 10 && bytes[1] != 0; // varint follows and won't be zero
  }
}
