/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.collector.kafka;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.Codec;
import zipkin.InMemoryCollectorMetrics;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.StorageComponent;
import zipkin.TestObjects;
import zipkin.collector.kafka.KafkaCollector.Builder;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.TRACE;

public class KafkaCollectorTest {
  @ClassRule public static Timeout globalTimeout = Timeout.seconds(10);
  Producer<String, byte[]> producer = KafkaTestGraph.INSTANCE.producer();
  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics kafkaMetrics = metrics.forTransport("kafka");

  LinkedBlockingQueue<List<Span>> recvdSpans = new LinkedBlockingQueue<>();
  AsyncSpanConsumer consumer = (spans, callback) -> {
    recvdSpans.add(spans);
    callback.onSuccess(null);
  };

  @Test
  public void canSetMaxMessageSize() throws Exception {
    Builder builder = builder("max_message").maxMessageSize(1);

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
      assertThat(processor.connector.get().config().fetchMessageMaxBytes())
          .isEqualTo(1);
    }
  }

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleThriftSpan() throws Exception {
    Builder builder = builder("single_span");

    byte[] bytes = Codec.THRIFT.writeSpan(TRACE.get(0));
    producer.send(new KeyedMessage<>(builder.topic, bytes));

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
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
    producer.send(new KeyedMessage<>(builder.topic, bytes));

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(TestObjects.TRACE.size());
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    Builder builder = builder("multiple_spans_json");

    byte[] bytes = Codec.JSON.writeSpans(TRACE);
    producer.send(new KeyedMessage<>(builder.topic, bytes));

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messages()).isEqualTo(1);
    assertThat(kafkaMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(kafkaMetrics.spans()).isEqualTo(TestObjects.TRACE.size());
  }

  /** Ensures malformed spans don't hang the processor */
  @Test
  public void skipsMalformedData() throws Exception {
    Builder builder = builder("decoder_exception");

    producer.send(new KeyedMessage<>(builder.topic, Codec.THRIFT.writeSpans(TRACE)));
    producer.send(new KeyedMessage<>(builder.topic, new byte[0]));
    producer.send(new KeyedMessage<>(builder.topic, "[\"='".getBytes())); // screwed up json
    producer.send(new KeyedMessage<>(builder.topic, "malformed".getBytes()));
    producer.send(new KeyedMessage<>(builder.topic, Codec.THRIFT.writeSpans(TRACE)));

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.messagesDropped()).isEqualTo(3);
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  public void skipsOnConsumerException() throws Exception {
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

    producer.send(new KeyedMessage<>(builder.topic, Codec.THRIFT.writeSpans(TRACE)));
    producer.send(new KeyedMessage<>(builder.topic, Codec.THRIFT.writeSpans(TRACE))); // tossed on error
    producer.send(new KeyedMessage<>(builder.topic, Codec.THRIFT.writeSpans(TRACE)));

    try (KafkaCollector processor = newKafkaTransport(builder, consumer)) {
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(recvdSpans.take()).containsExactlyElementsOf(TRACE);
    }

    assertThat(kafkaMetrics.spansDropped()).isEqualTo(TestObjects.TRACE.size());
  }

  Builder builder(String topic) {
    return new Builder().metrics(metrics).zookeeper("127.0.0.1:2181").topic(topic);
  }

  KafkaCollector newKafkaTransport(Builder builder, AsyncSpanConsumer consumer) {
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

      @Override public void close() {
        throw new AssertionError();
      }
    })).start();
  }
}
