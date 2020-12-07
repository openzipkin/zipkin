/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
class ITRabbitMQCollector {
  @RegisterExtension RabbitMQExtension rabbit = new RabbitMQExtension();

  List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics rabbitmqMetrics = metrics.forTransport("rabbitmq");

  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  SpanConsumer consumer = (spans) -> {
    threadsProvidingSpans.add(Thread.currentThread());
    receivedSpans.add(spans);
    return Call.create(null);
  };
  Connection connection;

  @BeforeEach void setup() throws Exception {
    metrics.clear();
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbit.host());
    factory.setPort(rabbit.port());
    connection = factory.newConnection();
  }

  @AfterEach void tearDown() throws Exception {
    if (connection != null) connection.close();
  }

  @Test void checkPasses() throws Exception {
    try (RabbitMQCollector collector = builder("check_passes").build()) {
      assertThat(collector.check().ok()).isTrue();
    }
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

  void messageWithMultipleSpans(RabbitMQCollector.Builder builder, SpanBytesEncoder encoder)
    throws Exception {
    byte[] message = encoder.encodeList(spans);

    produceSpans(message, builder.queue);

    try (RabbitMQCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsAll(spans);
    }

    assertThat(rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbitmqMetrics.messagesDropped()).isZero();
    assertThat(rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbitmqMetrics.spans()).isEqualTo(spans.size());
    assertThat(rabbitmqMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test void skipsMalformedData() throws Exception {
    RabbitMQCollector.Builder builder = builder("decoder_exception");

    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    produceSpans(THRIFT.encodeList(spans), builder.queue);
    produceSpans(new byte[0], builder.queue);
    produceSpans(malformed1, builder.queue);
    produceSpans(malformed2, builder.queue);
    produceSpans(THRIFT.encodeList(spans), builder.queue);

    try (RabbitMQCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    }

    assertThat(rabbitmqMetrics.messages()).isEqualTo(5);
    assertThat(rabbitmqMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(rabbitmqMetrics.bytes())
      .isEqualTo(THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(rabbitmqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(rabbitmqMetrics.spansDropped()).isZero();
  }

  @Test void startsWhenConfiguredQueueDoesntExist() throws Exception {
    try (RabbitMQCollector collector = builder("ignored").queue("zipkin-test2").build()) {
      assertThat(collector.check().ok()).isTrue();
    }
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
    RabbitMQCollector.Builder builder = builder("storage_exception").storage(storage);

    produceSpans(THRIFT.encodeList(spans), builder.queue);
    produceSpans(THRIFT.encodeList(spans), builder.queue); // tossed on error
    produceSpans(THRIFT.encodeList(spans), builder.queue);

    try (RabbitMQCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    }

    assertThat(rabbitmqMetrics.messages()).isEqualTo(3);
    assertThat(rabbitmqMetrics.messagesDropped()).isZero(); // storage failure isn't a message failure
    assertThat(rabbitmqMetrics.bytes()).isEqualTo(THRIFT.encodeList(spans).length * 3);
    assertThat(rabbitmqMetrics.spans()).isEqualTo(spans.size() * 3);
    assertThat(rabbitmqMetrics.spansDropped()).isEqualTo(spans.size()); // only one dropped
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() throws Exception {
    try (RabbitMQCollector collector = builder("bugs bunny").build()) {
      collector.start();

      assertThat(collector).hasToString(
        String.format("RabbitMQCollector{addresses=[%s:%s], queue=%s}", rabbit.host(),
          rabbit.port(), "bugs bunny")
      );
    }
  }

  void produceSpans(byte[] spans, String queue) throws Exception {
    Channel channel = null;
    try {
      channel = connection.createChannel();
      channel.basicPublish("", queue, null, spans);
    } finally {
      if (channel != null) channel.close();
    }
  }

  RabbitMQCollector.Builder builder(String queue) {
    return rabbit.newCollectorBuilder(queue)
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
