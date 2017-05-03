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
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import zipkin.Codec;
import zipkin.Component;
import zipkin.Span;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.collector.kafka10.KafkaCollector.Builder;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.TRACE;

public class KafkaCollectorTest {

  @ClassRule public static Timeout globalTimeout = Timeout.seconds(20);

  public static EphemeralKafkaBroker broker = EphemeralKafkaBroker.create();
  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule(broker).waitForStartup();
  @Rule public ExpectedException thrown = ExpectedException.none();

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics kafkaMetrics = metrics.forTransport("kafka");

  LinkedBlockingQueue<List<Span>> recvdSpans = new LinkedBlockingQueue<>();
  AsyncSpanConsumer consumer = (spans, callback) -> {
    recvdSpans.add(spans);
    callback.onSuccess(null);
  };
  private KafkaProducer<byte[], byte[]> producer;

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
    try (KafkaCollector collector = newKafkaCollector(builder("check_passes"), consumer)) {
      assertThat(collector.check().ok).isTrue();
    }
  }

  @Test
  public void start_failsOnInvalidBootstrapServers() throws Exception {
    thrown.expect(KafkaException.class);
    thrown.expectMessage("Failed to construct kafka consumer");

    Builder builder = builder("fail_invalid_bootstrap_servers").bootstrapServers("1.1.1.1");

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      Thread.sleep(TimeUnit.SECONDS.toMillis(15));
      final Component.CheckResult checkResult = collector.check();
      assertThat(checkResult.ok).isFalse();
      if (checkResult.exception != null) {
        checkResult.exception.printStackTrace();
      }
    }
  }

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleThriftSpan() throws Exception {
    Builder builder = builder("single_span");

    byte[] bytes = Codec.THRIFT.writeSpan(TRACE.get(0));
    produceSpans(builder.topic, bytes);

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(TRACE.get(0));
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
    produceSpans(builder.topic, bytes);

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
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
    produceSpans(builder.topic, bytes);

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(TRACE.size());
  }

  /** Ensures malformed spans don't hang the collector */
  @Test
  public void skipsMalformedData() throws Exception {
    Builder builder = builder("decoder_exception");

    produceSpans(builder.topic, Codec.THRIFT.writeSpans(TRACE));
    produceSpans(builder.topic, new byte[0]);
    produceSpans(builder.topic, "[\"='".getBytes()); // screwed up json
    produceSpans(builder.topic, "malformed".getBytes());
    produceSpans(builder.topic, Codec.THRIFT.writeSpans(TRACE));

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messagesDropped()).isEqualTo(3);
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  public void skipsOnSpanConsumerException() throws Exception {
    Builder builder = builder("consumer_exception");

    AtomicInteger counter = new AtomicInteger();

    consumer = (spans, callback) -> {
      if (counter.getAndIncrement() == 1) {
        callback.onError(new RuntimeException("storage fell over"));
      } else {
        recvdSpans.add(spans);
        callback.onSuccess(null);
      }
    };

    produceSpans(builder.topic, Codec.THRIFT.writeSpans(TRACE));
    produceSpans(builder.topic, Codec.THRIFT.writeSpans(TRACE)); // tossed on error
    produceSpans(builder.topic, Codec.THRIFT.writeSpans(TRACE));

    try (KafkaCollector collector = newKafkaCollector(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.spansDropped()).isEqualTo(TRACE.size());
  }

  private void produceSpans(String topic, byte[] spans) {
    kafka.helper().produce(topic, producer, Collections.singletonMap(null, spans));
  }

  Builder builder(String topic) {
    return new Builder()
        .metrics(metrics)
        .bootstrapServers(broker.getBrokerList().get())
        .topic(topic)
        .groupId(topic + "_group");
  }

  KafkaCollector newKafkaCollector(Builder builder, AsyncSpanConsumer consumer) {
    return new KafkaCollector(builder.storage(new StorageComponent() {
      @Override public SpanStore spanStore() {
        throw new AssertionError();
      }

      @Override public AsyncSpanStore asyncSpanStore() {
        throw new AssertionError();
      }

      @Override public AsyncSpanConsumer asyncSpanConsumer() {
        return consumer;
      }

      @Override public CheckResult check() {
        return CheckResult.OK;
      }

      @Override public void close() {
        throw new AssertionError();
      }
    })).start();
  }
}
