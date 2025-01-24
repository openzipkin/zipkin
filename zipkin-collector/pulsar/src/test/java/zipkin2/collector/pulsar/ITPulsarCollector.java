/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package zipkin2.collector.pulsar;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.PROTO3;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@Tag("docker")
public class ITPulsarCollector {

  @RegisterExtension
  static PulsarExtension pulsar = new PulsarExtension();
  List<Span> spans = List.of(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics pulsarMetrics = metrics.forTransport("pulsar");
  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  SpanConsumer consumer;
  PulsarClient pulsarClient;
  PulsarCollector collector;
  String testName;

  @BeforeEach void start(TestInfo testInfo) throws PulsarClientException {
    Optional<Method> testMethod = testInfo.getTestMethod();
    if (testMethod.isPresent()) {
      this.testName = testMethod.get().getName();
    }
    metrics.clear();
    threadsProvidingSpans.clear();
    receivedSpans.clear();
    pulsarMetrics.clear();
    consumer = (spans) -> {
      threadsProvidingSpans.add(Thread.currentThread());
      receivedSpans.add(spans);
      return Call.create(null);
    };
    pulsarClient = PulsarClient.builder().serviceUrl(pulsar.serviceUrl()).build();
    collector = builder().build().start();
  }

  PulsarCollector.Builder builder() {
    return pulsar.newCollectorBuilder(testName)
        .storage(buildStorage(consumer))
        .metrics(metrics)
        .subscriptionName(testName)
        .topic(testName);
  }

  @AfterEach void tearDown() throws PulsarClientException {
    pulsarClient.close();
  }

  @Test void checkPasses() {
    assertThat(collector.check().ok()).isTrue();
  }

  @Test void startFailsWithInvalidServiceUrl() {
    Throwable exception = assertThrows(RuntimeException.class, () -> {
      collector = builder().serviceUrl("@zixin").build();
      collector.start();
    });
    assertThat(exception.getMessage()).contains("Pulsar client create failed");
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() throws IOException {
    try (PulsarCollector collector = builder().build()) {
      assertThat(collector).hasToString(String.format(
          "PulsarCollector{clientProps={serviceUrl=%s}, consumerProps={subscriptionName=%s}, topic=%s}",
          pulsar.serviceUrl(),
          testName,
          testName
      ));
    }
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

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test void messageWithMultipleSpans_thrift() throws Exception {
    messageWithMultipleSpans(THRIFT);
  }

  /** Ensures malformed spans don't hang the collector */
  @Test void skipsMalformedData() throws Exception {
    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    pushMessage(collector.topic, THRIFT.encodeList(spans));
    pushMessage(collector.topic, new byte[0]);
    pushMessage(collector.topic, malformed1);
    pushMessage(collector.topic, malformed2);
    pushMessage(collector.topic, THRIFT.encodeList(spans));

    Thread.sleep(1000);

    assertThat(pulsarMetrics.messages()).isEqualTo(5);
    assertThat(pulsarMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(pulsarMetrics.bytes()).isEqualTo(
        THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(pulsarMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(pulsarMetrics.spansDropped()).isZero();
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test void skipsOnSpanStorageException() throws Exception {
    collector.close();

    AtomicInteger counter = new AtomicInteger();
    consumer = (input) -> new Call.Base<>() {
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

    pushMessage(collector.topic, PROTO3.encodeList(spans));
    pushMessage(collector.topic, PROTO3.encodeList(spans)); // tossed on error
    pushMessage(collector.topic, PROTO3.encodeList(spans));

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    // the only way we could read this, is if the malformed span was skipped.
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(pulsarMetrics.messages()).isEqualTo(3);
    assertThat(pulsarMetrics.messagesDropped()).isZero(); // storage failure not message failure
    assertThat(pulsarMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 3);
    assertThat(pulsarMetrics.spans()).isEqualTo(spans.size() * 3);
    assertThat(pulsarMetrics.spansDropped()).isEqualTo(spans.size()); // only one dropped
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

    pushMessage(collector.topic, new byte[]{}); // empty bodies don't go to storage
    pushMessage(collector.topic, PROTO3.encodeList(spans));
    pushMessage(collector.topic, PROTO3.encodeList(spans));

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    latch.countDown();
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(threadsProvidingSpans).hasSize(2);

    assertThat(pulsarMetrics.messages()).isEqualTo(3); // 2 + empty body for warmup
    assertThat(pulsarMetrics.messagesDropped()).isZero();
    assertThat(pulsarMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 2);
    assertThat(pulsarMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(pulsarMetrics.spansDropped()).isZero();
  }


  private void messageWithMultipleSpans(SpanBytesEncoder encoder) throws Exception {
    byte[] message = encoder.encodeList(spans);
    pushMessage(collector.topic, message);

    assertThat(receivedSpans.take()).containsAll(spans);
    assertThat(pulsarMetrics.messages()).isEqualTo(1);
    assertThat(pulsarMetrics.messagesDropped()).isZero();
    assertThat(pulsarMetrics.bytes()).isEqualTo(message.length);
    assertThat(pulsarMetrics.spans()).isEqualTo(spans.size());
    assertThat(pulsarMetrics.spansDropped()).isZero();
  }

  private void pushMessage(String topic, byte[] message) {
    try (Producer<byte[]> producer = pulsarClient.newProducer().topic(topic).create()) {
      producer.newMessage().value(message).send();
    } catch (PulsarClientException e) {
      throw new RuntimeException("Unable to send message to Pulsar", e);
    }
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
