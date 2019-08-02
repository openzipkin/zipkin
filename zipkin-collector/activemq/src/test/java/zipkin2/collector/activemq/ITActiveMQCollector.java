/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.collector.activemq;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
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
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.PROTO3;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

public class ITActiveMQCollector {
  List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  @ClassRule public static EmbeddedActiveMQBroker activemq = new EmbeddedActiveMQBroker();
  @Rule public TestName testName = new TestName();
  @Rule public ExpectedException thrown = ExpectedException.none();

  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics activemqMetrics = metrics.forTransport("activemq");

  CopyOnWriteArraySet<Thread> threadsProvidingSpans = new CopyOnWriteArraySet<>();
  LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();
  SpanConsumer consumer = (spans) -> {
    threadsProvidingSpans.add(Thread.currentThread());
    receivedSpans.add(spans);
    return Call.create(null);
  };

  ActiveMQCollector collector;

  @Before public void start() {
    collector = builder().build().start();
  }

  @After public void stop() throws IOException {
    collector.close();
  }

  @Test public void checkPasses() {
    assertThat(collector.check().ok()).isTrue();
  }

  @Test public void startFailsWithInvalidActiveMqServer() throws Exception {
    collector.close();

    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
    // we can be pretty certain ActiveMQ isn't running on localhost port 80
    connectionFactory.setBrokerURL("tcp://localhost:80");
    collector = builder().connectionFactory(connectionFactory).build();

    thrown.expect(UncheckedIOException.class);
    thrown.expectMessage("Unable to establish connection to ActiveMQ broker: Connection refused");
    collector.start();
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(collector).hasToString(String.format("ActiveMQCollector{brokerURL=%s, queue=%s}",
      activemq.getVmURL(), testName.getMethodName())
    );
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test public void messageWithMultipleSpans_json() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V1);
  }

  /** Ensures list encoding works: a version 2 json list of spans */
  @Test public void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V2);
  }

  /** Ensures list encoding works: proto3 ListOfSpans */
  @Test public void messageWithMultipleSpans_proto3() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.PROTO3);
  }

  void messageWithMultipleSpans(SpanBytesEncoder encoder) throws Exception {
    byte[] message = encoder.encodeList(spans);
    activemq.pushMessage(collector.queue, message);

    assertThat(receivedSpans.take()).isEqualTo(spans);

    assertThat(activemqMetrics.messages()).isEqualTo(1);
    assertThat(activemqMetrics.messagesDropped()).isZero();
    assertThat(activemqMetrics.bytes()).isEqualTo(message.length);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size());
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test public void skipsMalformedData() throws Exception {
    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    activemq.pushMessage(collector.queue, THRIFT.encodeList(spans));
    activemq.pushMessage(collector.queue, new byte[0]);
    activemq.pushMessage(collector.queue, malformed1);
    activemq.pushMessage(collector.queue, malformed2);
    activemq.pushMessage(collector.queue, THRIFT.encodeList(spans));

    Thread.sleep(1000);

    assertThat(activemqMetrics.messages()).isEqualTo(5);
    assertThat(activemqMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(activemqMetrics.bytes())
      .isEqualTo(THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test public void skipsOnSpanStorageException() throws Exception {
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

    activemq.pushMessage(collector.queue, PROTO3.encodeList(spans));
    activemq.pushMessage(collector.queue, PROTO3.encodeList(spans)); // tossed on error
    activemq.pushMessage(collector.queue, PROTO3.encodeList(spans));

    collector = builder().storage(buildStorage(consumer)).build().start();

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    // the only way we could read this, is if the malformed span was skipped.
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(activemqMetrics.messages()).isEqualTo(3);
    assertThat(activemqMetrics.messagesDropped()).isZero(); // storage failure not message failure
    assertThat(activemqMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 3);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 3);
    assertThat(activemqMetrics.spansDropped()).isEqualTo(spans.size()); // only one dropped
  }

  @Test public void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
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

    activemq.pushMessage(collector.queue, ""); // empty bodies don't go to storage
    activemq.pushMessage(collector.queue, PROTO3.encodeList(spans));
    activemq.pushMessage(collector.queue, PROTO3.encodeList(spans));

    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);
    latch.countDown();
    assertThat(receivedSpans.take()).containsExactlyElementsOf(spans);

    assertThat(threadsProvidingSpans.size()).isEqualTo(2);

    assertThat(activemqMetrics.messages()).isEqualTo(3); // 2 + empty body for warmup
    assertThat(activemqMetrics.messagesDropped()).isZero();
    assertThat(activemqMetrics.bytes()).isEqualTo(PROTO3.encodeList(spans).length * 2);
    assertThat(activemqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(activemqMetrics.spansDropped()).isZero();
  }

  ActiveMQCollector.Builder builder() {
    return ActiveMQCollector.builder()
      .connectionFactory(activemq.createConnectionFactory())
      .storage(buildStorage(consumer))
      .metrics(metrics)
      // prevent test flakes by having each run in an individual queue
      .queue(testName.getMethodName());
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
