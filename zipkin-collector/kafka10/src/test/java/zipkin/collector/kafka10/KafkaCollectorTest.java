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

import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.curator.test.InstanceSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import zipkin.Codec;
import zipkin.Span;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.collector.kafka10.KafkaCollector.Builder;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.V2SpanConverter;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;
import static zipkin.TestObjects.TRACE;

public class KafkaCollectorTest {

  private static final int RANDOM_PORT = -1;
  private static final EphemeralKafkaBroker broker =
      EphemeralKafkaBroker.create(RANDOM_PORT, RANDOM_PORT, buildBrokerConfig());

  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule(broker).waitForStartup();
  @ClassRule public static Timeout globalTimeout = Timeout.seconds(30);
  @Rule public ExpectedException thrown = ExpectedException.none();

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics kafkaMetrics = metrics.forTransport("kafka");

  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  AsyncSpanConsumer consumer = (spans, callback) -> {
    threadsProvidingSpans.add(Thread.currentThread());
    receivedSpans.add(spans);
    callback.onSuccess(null);
  };
  private KafkaProducer<byte[], byte[]> producer;

  private static Properties buildBrokerConfig() {
    final Properties config = new Properties();
    config.setProperty("num.partitions", "2");
    return config;
  }

  @Before
  public void setup() {
    producer = kafka.helper().createByteProducer();
  }

  @After
  public void teardown() {
    producer.close();
  }

  @Test
  public void checkPasses() throws Exception {
    try (KafkaCollector collector = builder("check_passes").build()) {
      assertThat(collector.check().ok).isTrue();
    }
  }

  @Test
  public void start_failsOnInvalidBootstrapServers() throws Exception {
    thrown.expect(KafkaException.class);
    thrown.expectMessage("Failed to construct kafka consumer");

    Builder builder = builder("fail_invalid_bootstrap_servers").bootstrapServers("1.1.1.1");

    try (KafkaCollector collector = builder.build()) {
      collector.start();
    }
  }

  /**
   * If the Kafka broker(s) specified in the connection string are not available, the Kafka
   * consumer library will attempt to reconnect indefinitely. The Kafka consumer will not throw
   * an exception and does not expose the status of its connection to the Kafka broker(s) in its
   * API. The only control over this behavior provided is setting the delay between reconnection
   * attempts. This is controlled through the consumer config property "reconnect.backoff.ms",
   * which defaults to a value of 50.
   *
   * In this case, "unavailable" means that the Kafka consumer cannot establish a connection to
   * at least one of the hostname/IP and port combinations provided in the bootstrap
   * brokers list.
   *
   * There is an opportunity to improve visibility by having {@link KafkaCollector#check()}
   * interrogate the metrics provided by the Kafka consumer (see
   * {@link org.apache.kafka.clients.consumer.KafkaConsumer#metrics()}) to determine whether
   * connectivity to Kafka appears to be up based on observed activity.
   */
  @Test
  public void reconnectsIndefinitelyAndReportsHealthyWhenKafkaUnavailable() throws Exception {
    Builder builder = builder("fail_invalid_bootstrap_servers")
        .bootstrapServers("localhost:" + InstanceSpec.getRandomPort());

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      assertThat(collector.check().ok).isTrue();
    }
  }

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleThriftSpan() throws Exception {
    Builder builder = builder("single_span");

    byte[] bytes = Codec.THRIFT.writeSpan(TRACE.get(0));
    produceSpans(bytes, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactly(TRACE.get(0));
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(1);
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test
  public void messageWithMultipleSpans_thrift() throws Exception {
    Builder builder = builder("multiple_spans_thrift");

    byte[] bytes = Codec.THRIFT.writeSpans(TRACE);
    produceSpans(bytes, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(TRACE.size());
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    Builder builder = builder("multiple_spans_json");

    byte[] bytes = Codec.JSON.writeSpans(TRACE);
    produceSpans(bytes, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(TRACE.size());
  }

  /** Ensures list encoding works: a version 2 json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json2() throws Exception {
    Builder builder = builder("multiple_spans_json2");

    List<Span> spans = Arrays.asList(
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]),
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1])
    );

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(V2SpanConverter.fromSpans(spans));

    produceSpans(message, builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsAll(spans);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(message.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(spans.size());
  }

  /** Ensures malformed spans don't hang the collector */
  @Test
  public void skipsMalformedData() throws Exception {
    Builder builder = builder("decoder_exception");

    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder.topic);
    produceSpans(new byte[0], builder.topic);
    produceSpans("[\"='".getBytes(), builder.topic); // screwed up json
    produceSpans("malformed".getBytes(), builder.topic);
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messagesDropped()).isEqualTo(3);
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  public void skipsOnSpanConsumerException() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    final StorageComponent storage = buildStorage((spans, callback) -> {
      if (counter.getAndIncrement() == 1) {
        callback.onError(new RuntimeException("storage fell over"));
      } else {
        receivedSpans.add(spans);
        callback.onSuccess(null);
      }
    });
    Builder builder = builder("consumer_exception").storage(storage);

    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder.topic);
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder.topic); // tossed on error
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder.topic);

    try (KafkaCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.spansDropped()).isEqualTo(TRACE.size());
  }

  @Test
  public void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
    Builder builder = builder("multi_thread", 2);

    warmUpTopic(builder.topic);

    final byte[] traceBytes = Codec.THRIFT.writeSpans(TRACE);
    try (KafkaCollector collector = builder.build()) {
      collector.start();
      waitForPartitionAssignments(collector);
      produceSpans(traceBytes, builder.topic, 0);
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
      produceSpans(traceBytes, builder.topic, 1);
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(threadsProvidingSpans.size()).isEqualTo(2);

    assertThat(kafkaMetrics.messages()).isEqualTo(3);
    assertThat(kafkaMetrics.bytes()).isEqualTo(traceBytes.length * 2);
    assertThat(kafkaMetrics.spans()).isEqualTo(TRACE.size() * 2);
  }

  @Test public void multipleTopicsCommaDelimited() throws Exception {
    try (KafkaCollector collector = builder("topic1,topic2").build()) {
      collector.start();

      assertThat(collector.kafkaWorkers.workers.get(0).kafkaConsumer.subscription())
        .containsExactly("topic1", "topic2");
    }
  }

  /**
   * Producing this empty message triggers auto-creation of the topic and gets things "warmed up"
   * on the broker before the consumers subscribe. Without this, the topic is auto-created when
   * the first consumer subscribes but there appears to be a race condition where the existence of
   * the topic is not known to the partition assignor when the consumer group goes through its
   * initial re-balance. As a result, no partitions are assigned, there are no further changes to
   * group membership to trigger another re-balance, and no messages are consumed. This initial
   * message is not necessary if the test broker is re-created for each test, but that increases
   * execution time for the suite by a factor of 10x (2-3s to ~25s on my local machine).
   */
  private void warmUpTopic(String topic) {
    produceSpans(new byte[0], topic);
  }

  /**
   * Wait until all kafka consumers created by the collector have at least one partition
   * assigned.
   */
  private void waitForPartitionAssignments(KafkaCollector collector) throws Exception {
    long consumersWithAssignments = 0;
    while (consumersWithAssignments < collector.kafkaWorkers.streams) {
      Thread.sleep(10);
      consumersWithAssignments = collector.kafkaWorkers.workers.stream()
          .filter(w -> !w.assignedPartitions.get().isEmpty())
          .count();
    }
  }

  private void produceSpans(byte[] spans, String topic) {
    produceSpans(spans, topic, 0);
  }

  private void produceSpans(byte[] spans, String topic, Integer partition) {
    producer.send(new ProducerRecord<>(topic, partition, null, spans));
    producer.flush();
  }

  Builder builder(String topic) {
    return builder(topic, 1);
  }

  Builder builder(String topic, int streams) {
    return new Builder()
        .metrics(metrics)
        .bootstrapServers(broker.getBrokerList().get())
        .topic(topic)
        .groupId(topic + "_group")
        .streams(streams)
        .storage(buildStorage(consumer));
  }

  private StorageComponent buildStorage(final AsyncSpanConsumer spanConsumer) {
    return new StorageComponent() {
        @Override public SpanStore spanStore() {
          throw new AssertionError();
        }

        @Override public AsyncSpanStore asyncSpanStore() {
          throw new AssertionError();
        }

        @Override public AsyncSpanConsumer asyncSpanConsumer() {
          return spanConsumer;
        }

        @Override public CheckResult check() {
          return CheckResult.OK;
        }

        @Override public void close() {
          throw new AssertionError();
        }
      };
  }
}
