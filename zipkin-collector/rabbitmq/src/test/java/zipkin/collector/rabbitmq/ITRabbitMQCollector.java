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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.V2SpanConverter;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;
import static zipkin.collector.rabbitmq.RabbitMQCollector.builder;

public class ITRabbitMQCollector {
  List<Span> spans = Arrays.asList(
    ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]),
    ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1])
  );

  @ClassRule
  public static RabbitMQCollectorRule rabbit = new RabbitMQCollectorRule("rabbitmq:3.6-alpine");

  @After public void clear() {
    rabbit.metrics.clear();
    rabbit.storage.clear();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void checkPasses() throws Exception {
    assertThat(rabbit.collector.check().ok).isTrue();
  }

  @Test public void startFailsWithInvalidRabbitMqServer() throws Exception {
    // we can be pretty certain RabbitMQ isn't running on localhost port 80
    String notRabbitMqAddress = "localhost:80";
    try (RabbitMQCollector collector = builder()
      .addresses(Collections.singletonList(notRabbitMqAddress)).build()) {
      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("Unable to establish connection to RabbitMQ server");
      collector.start();
    }
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test
  public void messageWithMultipleSpans_thrift() throws Exception {
    byte[] message = Codec.THRIFT.writeSpans(spans);
    rabbit.publish(message);

    Thread.sleep(1000);
    assertThat(rabbit.storage.acceptedSpanCount()).isEqualTo(spans.size());

    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbit.rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbit.rabbitmqMetrics.spans()).isEqualTo(spans.size());
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    byte[] message = Codec.JSON.writeSpans(spans);
    rabbit.publish(message);

    Thread.sleep(1000);
    assertThat(rabbit.storage.acceptedSpanCount()).isEqualTo(spans.size());

    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbit.rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbit.rabbitmqMetrics.spans()).isEqualTo(spans.size());
  }

  /** Ensures list encoding works: a version 2 json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json2() throws Exception {
    List<Span> spans = Arrays.asList(
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]),
      ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1])
    );

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(V2SpanConverter.fromSpans(spans));
    rabbit.publish(message);

    Thread.sleep(1000);
    assertThat(rabbit.storage.acceptedSpanCount()).isEqualTo(spans.size());

    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(1);
    assertThat(rabbit.rabbitmqMetrics.bytes()).isEqualTo(message.length);
    assertThat(rabbit.rabbitmqMetrics.spans()).isEqualTo(spans.size());
  }

  /** Ensures malformed spans don't hang the collector */
  @Test
  public void skipsMalformedData() throws Exception {
    rabbit.publish(Codec.THRIFT.writeSpans(spans));
    rabbit.publish(new byte[0]);
    rabbit.publish("[\"='".getBytes()); // screwed up json
    rabbit.publish("malformed".getBytes());
    rabbit.publish(Codec.THRIFT.writeSpans(spans));

    Thread.sleep(1000);
    assertThat(rabbit.rabbitmqMetrics.messages()).isEqualTo(5);
    assertThat(rabbit.rabbitmqMetrics.messagesDropped()).isEqualTo(3);
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  public void skipsOnSpanConsumerException() throws Exception {
    // TODO: reimplement
  }

  @Test
  public void messagesDistributedAcrossMultipleThreadsSuccessfully() throws Exception {
    // TODO: reimplement
  }
}
