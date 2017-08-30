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
package zipkin.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.testcontainers.containers.GenericContainer;
import zipkin.Codec;
import zipkin.Span;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.collector.rabbitmq.RabbitMqCollector.Builder;
import zipkin.collector.rabbitmq.RabbitMqCollector.LazyRabbitWorkers.RabbitCollectorStartupException;
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
import static zipkin.collector.rabbitmq.RabbitMqCollector.convertAddresses;

public class RabbitMqCollectorTest {

  private static final int RABBIT_PORT = 5672;
  private static final String RABBIT_DOCKER_IMAGE = "rabbitmq:3.6-alpine";

  @ClassRule public static GenericContainer rabbitmq =
    new GenericContainer(RABBIT_DOCKER_IMAGE)
      .withExposedPorts(RABBIT_PORT);
  @ClassRule public static Timeout globalTimeout = Timeout.seconds(180);

  @Rule public ExpectedException thrown = ExpectedException.none();

  private List<String> dockerRabbitAddress = Collections.singletonList(
    rabbitmq.getContainerIpAddress() + ":" + rabbitmq.getMappedPort(RABBIT_PORT));

  private InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  private InMemoryCollectorMetrics rabbitMetrics = metrics.forTransport("rabbitmq");

  private CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  private LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  private AsyncSpanConsumer consumer = (spans, callback) -> {
    threadsProvidingSpans.add(Thread.currentThread());
    receivedSpans.add(spans);
    callback.onSuccess(null);
  };

  @Test
  public void checkPasses() throws Exception {
    try (RabbitMqCollector collector = builder().build()) {
      assertThat(collector.check().ok).isTrue();
    }
  }

  @Test
  public void startFailsWithInvalidRabbitMqServer() throws Exception {
    // we can be pretty certain RabbitMQ isn't running on localhost port 80
    String notRabbitMqAddress = "localhost:80";
    try (RabbitMqCollector collector = builder()
        .addresses(Collections.singletonList(notRabbitMqAddress)).build()) {
      thrown.expect(RabbitCollectorStartupException.class);
      thrown.expectMessage("Unable to establish connection to RabbitMQ server");
      collector.start();
    }
  }

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleThriftSpan() throws Exception {
    Builder builder = builder();

    byte[] bytes = Codec.THRIFT.writeSpan(TRACE.get(0));
    produceSpans(bytes, builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactly(TRACE.get(0));
    }

    assertThat(rabbitMetrics.messages()).isEqualTo(1);
    assertThat(rabbitMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(rabbitMetrics.spans()).isEqualTo(1);
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test
  public void messageWithMultipleSpans_thrift() throws Exception {
    Builder builder = builder();

    byte[] bytes = Codec.THRIFT.writeSpans(TRACE);
    produceSpans(bytes, builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(rabbitMetrics.messages()).isEqualTo(1);
    assertThat(rabbitMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(rabbitMetrics.spans()).isEqualTo(TRACE.size());
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    Builder builder = builder();

    byte[] bytes = Codec.JSON.writeSpans(TRACE);
    produceSpans(bytes, builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(rabbitMetrics.messages()).isEqualTo(1);
    assertThat(rabbitMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(rabbitMetrics.spans()).isEqualTo(TRACE.size());
  }

  /** Ensures list encoding works: a version 2 json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json2() throws Exception {
    Builder builder = builder();

    List<Span> spans = Arrays.asList(
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]),
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1])
    );

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(V2SpanConverter.fromSpans(spans));

    produceSpans(message, builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      // don't wait forever if no messages are in the queue
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsAll(spans);
    }

    assertThat(rabbitMetrics.messages()).isEqualTo(1);
    assertThat(rabbitMetrics.messagesDropped()).isZero();
    assertThat(rabbitMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbitMetrics.spans()).isEqualTo(spans.size());
  }

  /** Ensures malformed spans don't hang the collector */
  @Test
  public void skipsMalformedData() throws Exception {
    Builder builder = builder();

    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder);
    produceSpans(new byte[0], builder);
    produceSpans("[\"='".getBytes(), builder); // screwed up json
    produceSpans("malformed".getBytes(), builder);
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
      // the only way we could read this is if the malformed spans were skipped.
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
    }

    assertThat(rabbitMetrics.messagesDropped()).isEqualTo(3);
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
    Builder builder = builder().storage(storage);

    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder);
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder); // tossed on error
    produceSpans(Codec.THRIFT.writeSpans(TRACE), builder);

    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
    }

    assertThat(rabbitMetrics.spansDropped()).isEqualTo(TRACE.size());
  }

  @Test
  public void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
    Builder builder = builder().concurrency(2);

    final byte[] traceBytes = Codec.THRIFT.writeSpans(TRACE);
    try (RabbitMqCollector collector = builder.build()) {
      collector.start();
      produceSpans(traceBytes, builder);
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
      produceSpans(traceBytes, builder);
      assertThat(receivedSpans.poll(1, TimeUnit.SECONDS)).containsExactlyElementsOf(TRACE);
    }

    assertThat(threadsProvidingSpans.size()).isEqualTo(2);

    assertThat(rabbitMetrics.messages()).isEqualTo(2);
    assertThat(rabbitMetrics.bytes()).isEqualTo(traceBytes.length * 2);
    assertThat(rabbitMetrics.spans()).isEqualTo(TRACE.size() * 2);
  }

  private void produceSpans(byte[] message, Builder builder) throws IOException, TimeoutException {
    new RabbitMqProducer(builder).publishMessage(message, builder.queue).close();
  }

  private Builder builder() {
    return new Builder()
      .addresses(dockerRabbitAddress)
      .connectionFactory(new ConnectionFactory())
      .metrics(metrics)
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

  private class RabbitMqProducer {
    private final Connection connection;
    private final Channel channel;

    RabbitMqProducer(Builder builder) throws IOException, TimeoutException {
      this.connection =
        builder.connectionFactory.newConnection(convertAddresses(builder.addresses));
      this.channel = this.connection.createChannel();
      // without a durable queue existing, messages published before the collector is started are lost
      this.channel.queueDeclare(builder.queue, true, false, false, null);
    }

    RabbitMqProducer publishMessage(byte[] message, String queue) throws IOException {
      this.channel.basicPublish("", queue, null, message);
      return this;
    }

    void close() throws IOException {
      this.connection.close();
    }
  }
}
