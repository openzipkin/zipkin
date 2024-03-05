/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.kafka;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.ForwardingStorageComponent;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.JSON_V2;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@Tag("docker")
class ITKafkaCollector {
  @RegisterExtension static KafkaExtension kafka = new KafkaExtension();

  List<Span> spans = List.of(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics kafkaMetrics = metrics.forTransport("kafka");

  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  SpanConsumer consumer = (spans) -> {
    threadsProvidingSpans.add(Thread.currentThread());
    receivedSpans.add(spans);
    return Call.create(null);
  };
  KafkaProducer<byte[], byte[]> producer;

  @BeforeEach void setup() {
    metrics.clear();
    threadsProvidingSpans.clear();
    Properties config = new Properties();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServer());
    producer = new KafkaProducer<>(config, new ByteArraySerializer(), new ByteArraySerializer());
  }

  @AfterEach void tearDown() {
    if (producer != null) producer.close();
  }

  @Test void checkPasses() {
    try (KafkaCollector collector = builder("check_passes").build()) {
      assertThat(collector.check().ok()).isTrue();
    }
  }

  /**
   * Don't raise exception (crash process), rather fail status check! This allows the health check
   * to report the cause.
   */
  @Test void check_failsOnInvalidBootstrapServers() throws Exception {

    KafkaCollector.Builder builder =
      builder("fail_invalid_bootstrap_servers").bootstrapServers("1.1.1.1");

    try (KafkaCollector collector = builder.build()) {
      collector.start();

      Thread.sleep(1000L); // wait for crash

      assertThat(collector.check().error())
        .isInstanceOf(KafkaException.class)
        .hasMessage("Invalid url in bootstrap.servers: 1.1.1.1");
    }
  }

  /**
   * If the Kafka broker(s) specified in the connection string are not available, the Kafka consumer
   * library will attempt to reconnect indefinitely. The Kafka consumer will not throw an exception
   * and does not expose the status of its connection to the Kafka broker(s) in its API.
   * <p>
   * An AdminClient API instance has been added to the connector to validate that connection with
   * Kafka is available in every health check. This AdminClient reuses Consumer's properties to
   * Connect to the cluster, and request a Cluster description to validate communication with
   * Kafka.
   */
  @Test void reconnectsIndefinitelyAndReportsUnhealthyWhenKafkaUnavailable() throws Exception {
    KafkaCollector.Builder builder =
      builder("fail_invalid_bootstrap_servers").bootstrapServers("localhost:" + 9092);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      assertThat(collector.check().error()).isInstanceOf(TimeoutException.class);
    }
  }

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test void messageWithSingleThriftSpan() throws Exception {
    KafkaCollector.Builder builder = builder("single_span");

    byte[] bytes = THRIFT.encode(CLIENT_SPAN);
    produceSpans(bytes, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactly(CLIENT_SPAN);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.messagesDropped()).isZero();
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(1);
    assertThat(kafkaMetrics.spansDropped()).isZero();
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test void messageWithMultipleSpans_thrift() throws Exception {
    messageWithMultipleSpans(builder("multiple_spans_thrift"), THRIFT);
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test void messageWithMultipleSpans_json() throws Exception {
    messageWithMultipleSpans(builder("multiple_spans_json"), SpanBytesEncoder.JSON_V1);
  }

  /** Ensures list encoding works: a version 2 json list of spans */
  @Test void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(builder("multiple_spans_json2"), SpanBytesEncoder.JSON_V2);
  }

  /** Ensures list encoding works: proto3 ListOfSpans */
  @Test void messageWithMultipleSpans_proto3() throws Exception {
    messageWithMultipleSpans(builder("multiple_spans_proto3"), SpanBytesEncoder.PROTO3);
  }

  void messageWithMultipleSpans(KafkaCollector.Builder builder, SpanBytesEncoder encoder)
    throws Exception {
    byte[] message = encoder.encodeList(spans);

    produceSpans(message, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsAll(spans);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.messagesDropped()).isZero();
    assertThat(kafkaMetrics.bytes()).isEqualTo(message.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(spans.size());
    assertThat(kafkaMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test void skipsMalformedData() throws Exception {
    KafkaCollector.Builder builder = builder("decoder_exception");

    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    produceSpans(THRIFT.encodeList(spans), builder.topic);
    produceSpans(new byte[0], builder.topic);
    produceSpans(malformed1, builder.topic);
    produceSpans(malformed2, builder.topic);
    produceSpans(THRIFT.encodeList(spans), builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(5);
    assertThat(kafkaMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(kafkaMetrics.bytes())
      .isEqualTo(THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(kafkaMetrics.spansDropped()).isZero();
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test void skipsOnSpanStorageException() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    consumer = (input) -> new Call.Base<Void>() {
      @Override protected Void doExecute() {
        throw new AssertionError();
      }

      @Override protected void doEnqueue(Callback<Void> callback) {
        if (counter.getAndIncrement() == 1) {
          callback.onError(new RuntimeException("storage fell over"));
        } else {
          receivedSpans.add(spans);
          callback.onSuccess(null);
        }
      }

      @Override public Call<Void> clone() {
        throw new AssertionError();
      }
    };
    final StorageComponent storage = buildStorage(consumer);
    KafkaCollector.Builder builder = builder("storage_exception").storage(storage);

    produceSpans(THRIFT.encodeList(spans), builder.topic);
    produceSpans(THRIFT.encodeList(spans), builder.topic); // tossed on error
    produceSpans(THRIFT.encodeList(spans), builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(3);
    assertThat(kafkaMetrics.messagesDropped()).isZero(); // storage failure isn't a message failure
    assertThat(kafkaMetrics.bytes()).isEqualTo(THRIFT.encodeList(spans).length * 3);
    assertThat(kafkaMetrics.spans()).isEqualTo(spans.size() * 3);
    assertThat(kafkaMetrics.spansDropped()).isEqualTo(spans.size()); // only one dropped
  }

  @Test void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
    KafkaCollector.Builder builder = builder("multi_thread", 2);

    kafka.prepareTopics(builder.topic, 2);
    warmUpTopic(builder.topic);

    final byte[] traceBytes = JSON_V2.encodeList(spans);
    try (KafkaCollector collector = builder.build()) {
      collector.start();
      waitForPartitionAssignments(collector);
      produceSpans(traceBytes, builder.topic, 0);
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
      produceSpans(traceBytes, builder.topic, 1);
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    }

    assertThat(threadsProvidingSpans).hasSize(2);

    assertThat(kafkaMetrics.messages()).isEqualTo(3); // 2 + empty body for warmup
    assertThat(kafkaMetrics.messagesDropped()).isZero();
    assertThat(kafkaMetrics.bytes()).isEqualTo(traceBytes.length * 2);
    assertThat(kafkaMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(kafkaMetrics.spansDropped()).isZero();
  }

  @Test void multipleTopicsCommaDelimited() {
    try (KafkaCollector collector = builder("topic1,topic2").build()) {
      collector.start();

      assertThat(collector.kafkaWorkers.workers.get(0).topics).containsExactly("topic1", "topic2");
    }
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    try (KafkaCollector collector = builder("muah").build()) {
      collector.start();

      assertThat(collector).hasToString(
        "KafkaCollector{bootstrapServers=%s, topic=%s}".formatted(kafka.bootstrapServer(),
          "muah")
      );
    }
  }

  /**
   * Producing this empty message triggers auto-creation of the topic and gets things "warmed up" on
   * the broker before the consumers subscribe. Without this, the topic is auto-created when the
   * first consumer subscribes but there appears to be a race condition where the existence of the
   * topic is not known to the partition assignor when the consumer group goes through its initial
   * re-balance. As a result, no partitions are assigned, there are no further changes to group
   * membership to trigger another re-balance, and no messages are consumed. This initial message is
   * not necessary if the test broker is re-created for each test, but that increases execution time
   * for the suite by a factor of 10x (2-3s to ~25s on my local machine).
   */
  void warmUpTopic(String topic) {
    produceSpans(new byte[0], topic);
  }

  /**
   * Wait until all kafka consumers created by the collector have at least one partition assigned.
   */
  void waitForPartitionAssignments(KafkaCollector collector) throws Exception {
    long consumersWithAssignments = 0;
    while (consumersWithAssignments < collector.kafkaWorkers.streams) {
      Thread.sleep(10);
      consumersWithAssignments =
        collector
          .kafkaWorkers
          .workers
          .stream()
          .filter(w -> !w.assignedPartitions.get().isEmpty())
          .count();
    }
  }

  void produceSpans(byte[] spans, String topic) {
    produceSpans(spans, topic, 0);
  }

  void produceSpans(byte[] spans, String topic, Integer partition) {
    producer.send(new ProducerRecord<>(topic, partition, null, spans));
    producer.flush();
  }

  KafkaCollector.Builder builder(String topic) {
    return builder(topic, 1);
  }

  KafkaCollector.Builder builder(String topic, int streams) {
    return kafka.newCollectorBuilder(topic, streams)
      .metrics(metrics)
      .storage(buildStorage(consumer));
  }

  static StorageComponent buildStorage(final SpanConsumer spanConsumer) {
    return new ForwardingStorageComponent() {
      @Override protected StorageComponent delegate() {
        throw new AssertionError();
      }

      @Override public SpanConsumer spanConsumer() {
        return spanConsumer;
      }
    };
  }
}
