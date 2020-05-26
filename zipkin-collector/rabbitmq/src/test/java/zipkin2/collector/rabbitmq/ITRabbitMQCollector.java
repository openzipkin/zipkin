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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.JSON_V2;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;

public class ITRabbitMQCollector {
  List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  @ClassRule public static RabbitMQCollectorRule rabbit = new RabbitMQCollectorRule();

  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics rabbitmqMetrics = metrics.forTransport("rabbitmq");
  RabbitMQCollector collector = rabbit.tryToInitializeCollector(newCollectorBuilder()).start();

  @After public void after() throws Exception {
    collector.close();
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
    publish(message);

    Thread.sleep(200L);
    assertThat(storage.acceptedSpanCount()).isEqualTo(spans.size());

    assertThat(rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbitmqMetrics.messagesDropped()).isZero();
    assertThat(rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbitmqMetrics.spans()).isEqualTo(spans.size());
    assertThat(rabbitmqMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test public void skipsMalformedData() throws Exception {
    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    publish(THRIFT.encodeList(spans));
    publish(new byte[0]);
    publish(malformed1);
    publish(malformed2);
    publish(THRIFT.encodeList(spans));

    Thread.sleep(200L);

    assertThat(rabbitmqMetrics.messages()).isEqualTo(5);
    assertThat(rabbitmqMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(rabbitmqMetrics.bytes())
      .isEqualTo(THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(rabbitmqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(rabbitmqMetrics.spansDropped()).isZero();
  }

  /** See GitHub issue #2068 */
  @Test
  public void startsWhenConfiguredQueueAlreadyExists() throws Exception {
    String differentQueue = "zipkin-test2";

    rabbit.declareQueue(differentQueue);
    collector.close();
    collector = rabbit.tryToInitializeCollector(newCollectorBuilder().queue(differentQueue)).start();

    publish(JSON_V2.encodeList(spans));

    Thread.sleep(200L);
    assertThat(storage.acceptedSpanCount()).isEqualTo(spans.size());
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test public void skipsOnSpanConsumerException() {
    // TODO: reimplement
  }

  @Test public void messagesDistributedAcrossMultipleThreadsSuccessfully() {
    // TODO: reimplement
  }

  RabbitMQCollector.Builder newCollectorBuilder() {
    return rabbit.newCollectorBuilder().storage(storage).metrics(metrics);
  }

  void publish(byte[] message) throws IOException, TimeoutException {
    Channel channel = collector.connection.get().createChannel();
    try {
      channel.basicPublish("", collector.queue, null, message);
    } finally {
      channel.close();
    }
  }
}
