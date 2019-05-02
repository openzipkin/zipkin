/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.CollectorMetrics;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.codec.SpanBytesEncoder.THRIFT;
import static zipkin2.collector.rabbitmq.RabbitMQCollector.builder;

public class ITRabbitMQCollector {
  List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  @ClassRule
  public static RabbitMQCollectorRule rabbit = new RabbitMQCollectorRule("rabbitmq:3.7-alpine");

  @After
  public void clear() {
    rabbit.metrics.clear();
    rabbit.storage.clear();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void checkPasses() {
    assertThat(rabbit.collector.check().ok()).isTrue();
  }

  @Test
  public void startFailsWithInvalidRabbitMqServer() throws Exception {
    // we can be pretty certain RabbitMQ isn't running on localhost port 80
    String notRabbitMqAddress = "localhost:80";
    try (RabbitMQCollector collector =
        builder().addresses(Collections.singletonList(notRabbitMqAddress)).build()) {
      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("Unable to establish connection to RabbitMQ server");
      collector.start();
    }
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V1);
  }

  /** Ensures list encoding works: a version 2 json list of spans */
  @Test
  public void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V2);
  }

  /** Ensures list encoding works: proto3 ListOfSpans */
  @Test
  public void messageWithMultipleSpans_proto3() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.PROTO3);
  }

  void messageWithMultipleSpans(SpanBytesEncoder encoder) throws Exception {
    byte[] message = encoder.encodeList(spans);
    rabbit.publish(message);

    Thread.sleep(1000);
    assertThat(rabbit.storage.acceptedSpanCount()).isEqualTo(spans.size());

    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbit.rabbitmqMetrics.messagesDropped()).isZero();
    assertThat(rabbit.rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbit.rabbitmqMetrics.spans()).isEqualTo(spans.size());
    assertThat(rabbit.rabbitmqMetrics.spansDropped()).isZero();
  }

  /** Ensures malformed spans don't hang the collector */
  @Test
  public void skipsMalformedData() throws Exception {
    byte[] malformed1 = "[\"='".getBytes(UTF_8); // screwed up json
    byte[] malformed2 = "malformed".getBytes(UTF_8);
    rabbit.publish(THRIFT.encodeList(spans));
    rabbit.publish(new byte[0]);
    rabbit.publish(malformed1);
    rabbit.publish(malformed2);
    rabbit.publish(THRIFT.encodeList(spans));

    Thread.sleep(1000);

    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(5);
    assertThat(rabbit.rabbitmqMetrics.messagesDropped()).isEqualTo(2); // only malformed, not empty
    assertThat(rabbit.rabbitmqMetrics.bytes())
      .isEqualTo(THRIFT.encodeList(spans).length * 2 + malformed1.length + malformed2.length);
    assertThat(rabbit.rabbitmqMetrics.spans()).isEqualTo(spans.size() * 2);
    assertThat(rabbit.rabbitmqMetrics.spansDropped()).isZero();
  }

  /** See GitHub issue #2068 */
  @Test
  public void startsWhenConfiguredQueueAlreadyExists() throws IOException, TimeoutException {
    Channel channel = rabbit.collector.connection.get().createChannel();
    // make a queue with non-default properties
    channel.queueDeclare("zipkin-test2", true, false, false, Collections.singletonMap("x-message-ttl", 36000000));
    try {
      RabbitMQCollector.builder()
        .storage(InMemoryStorage.newBuilder().build())
        .metrics(CollectorMetrics.NOOP_METRICS)
        .queue("zipkin-test2")
        .addresses(Collections.singletonList(rabbit.address())).build()
        .start().close();
    } finally {
      channel.queueDelete("zipkin-test2");
      channel.close();
    }
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  public void skipsOnSpanConsumerException() {
    // TODO: reimplement
  }

  @Test
  public void messagesDistributedAcrossMultipleThreadsSuccessfully() {
    // TODO: reimplement
  }
}
