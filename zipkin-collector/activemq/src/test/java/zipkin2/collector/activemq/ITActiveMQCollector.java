/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.activemq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.PROTO3;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@Tag("docker")
class ITActiveMQCollector {
  @RegisterExtension static ActiveMQExtension activemq = new ActiveMQExtension();
  List<Span> spans = List.of(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  public String testName;

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics activemqMetrics = metrics.forTransport("activemq");

  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  SpanConsumer consumer;
  ActiveMQCollector collector;

  @BeforeEach void start(TestInfo testInfo) {
    Optional<Method> testMethod = testInfo.getTestMethod();
    if (testMethod.isPresent()) {
      this.testName = testMethod.get().getName();
    }
    threadsProvidingSpans.clear();
    receivedSpans.clear();
    consumer = (spans) -> {
      threadsProvidingSpans.add(Thread.currentThread());
      receivedSpans.add(spans);
      return Call.create(null);
    };
    activemqMetrics.clear();
    collector = builder().build().start();
  }

  @AfterEach void stop() throws IOException {
    collector.close();
  }

  @Test void checkPasses() {
    assertThat(collector.check().ok()).isTrue();
  }

  @Test void startFailsWithInvalidActiveMqServer() {
    Throwable exception = assertThrows(UncheckedIOException.class, () -> {
      collector.close();

      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
      // we can be pretty certain ActiveMQ isn't running on localhost port 80
      connectionFactory.setBrokerURL("tcp://localhost:80");
      collector = builder().connectionFactory(connectionFactory).build();
      collector.start();
    });
    assertThat(exception.getMessage()).contains("Unable to establish connection to ActiveMQ broker: Connection refused");
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    assertThat(collector).hasToString(
      "ActiveMQCollector{brokerURL=%s, queue=%s}".formatted(activemq.brokerURL(), testName));
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test void messageWithMultipleSpans_json() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V1);
  }

  /** Ensures list encoding works: a version 2 json list of spans */
  @Test void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V2);
  }

  /** Ensures list encoding works: proto3 ListOfSpans */
  @Test void messageWithMultipleSpans_proto3() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.PROTO3);
  }

  void messageWithMultipleSpans(SpanBytesEncoder encoder) throws Exception {
    byte[] message = encoder.encodeList(spans);
    pushMessage(collector.queue, message);

    assertThat(receivedSpans.take()).isEqualTo(spans);

    assertThat(activemqMetrics.messages()).isEqualTo(1);
    assertThat(activemqMetrics.messagesDropped()).isZero();
    assertThat(activemqMetrics.bytes()).isEqualTo(message.length);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size());
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test void skipsMalformedData() throws Exception {
    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    pushMessage(collector.queue, THRIFT.encodeList(spans));
    pushMessage(collector.queue, new byte[0]);
    pushMessage(collector.queue, malformed1);
    pushMessage(collector.queue, malformed2);
    pushMessage(collector.queue, THRIFT.encodeList(spans));

    Thread.sleep(1000);

    assertThat(activemqMetrics.messages()).isEqualTo(5);
    assertThat(activemqMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(activemqMetrics.bytes()).isEqualTo(
      THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test void skipsOnSpanStorageException() throws Exception {
    collector.close();

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

    collector = builder().storage(buildStorage(consumer)).build().start();

    pushMessage(collector.queue, PROTO3.encodeList(spans));
    pushMessage(collector.queue, PROTO3.encodeList(spans)); // tossed on error
    pushMessage(collector.queue, PROTO3.encodeList(spans));

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    // the only way we could read this, is if the malformed span was skipped.
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(activemqMetrics.messages()).isEqualTo(3);
    assertThat(activemqMetrics.messagesDropped()).isZero(); // storage failure not message failure
    assertThat(activemqMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 3);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 3);
    assertThat(activemqMetrics.spansDropped()).isEqualTo(spans.size()); // only one dropped
  }

  @Test void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
    collector.close();

    CountDownLatch latch = new CountDownLatch(2);
    collector = builder().concurrency(2).storage(buildStorage((spans) -> {
      latch.countDown();
      try {
        latch.await(); // await the other one as this proves 2 threads are in use
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
      return consumer.accept(spans);
    })).build().start();

    pushMessage(collector.queue, new byte[] {}); // empty bodies don't go to storage
    pushMessage(collector.queue, PROTO3.encodeList(spans));
    pushMessage(collector.queue, PROTO3.encodeList(spans));

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    latch.countDown();
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(threadsProvidingSpans).hasSize(2);

    assertThat(activemqMetrics.messages()).isEqualTo(3); // 2 + empty body for warmup
    assertThat(activemqMetrics.messagesDropped()).isZero();
    assertThat(activemqMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 2);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  ActiveMQCollector.Builder builder() {
    // prevent test flakes by having each run in an individual queue
    return activemq.newCollectorBuilder(testName)
      .storage(buildStorage(consumer))
      .metrics(metrics)
      .queue(testName);
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

  void pushMessage(String queueName, byte[] message) throws Exception {
    ActiveMQSpanConsumer consumer = collector.lazyInit.result;

    // Look up the existing session for this queue, so that there is no chance of flakes.
    QueueSession session = null;
    Queue queue = null;
    for (Map.Entry<QueueSession, QueueReceiver> entry : consumer.sessionToReceiver.entrySet()) {
      if (entry.getValue().getQueue().getQueueName().equals(queueName)) {
        session = entry.getKey();
        queue = entry.getValue().getQueue();
        break;
      }
    }
    if (session == null) {
      throw new NoSuchElementException("couldn't find session for queue " + queueName);
    }

    Connection conn = collector.lazyInit.result.connection;

    try (QueueSender sender = session.createSender(queue)) {
      BytesMessage bytesMessage = session.createBytesMessage();
      bytesMessage.writeBytes(message);
      sender.send(bytesMessage);
    }
  }
}
