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
package zipkin.kafka;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.Timeout;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.async.AsyncSpanConsumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.SERVER_RECV;

public class KafkaTransportTest {
  @ClassRule public static Timeout globalTimeout = Timeout.seconds(10);
  Producer<String, byte[]> producer = KafkaTestGraph.INSTANCE.producer();

  Endpoint endpoint = Endpoint.create("web", 127 << 24 | 1, 80);
  Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, endpoint);
  Span span = new Span.Builder().traceId(1L).id(2L).timestamp(ann.timestamp).name("get")
      .addAnnotation(ann).build();

  LinkedBlockingQueue<List<Span>> recvdSpans = new LinkedBlockingQueue<>();
  AsyncSpanConsumer consumer = (spans, callback) -> {
    recvdSpans.add(spans);
    callback.onSuccess(null);
  };

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleThriftSpan() throws Exception {
    KafkaConfig config = configForTopic("single_span");

    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpan(span)));

    try (KafkaTransport processor = new KafkaTransport(config, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(span);
    }
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test
  public void messageWithMultipleSpans_thrift() throws Exception {
    KafkaConfig config = configForTopic("multiple_spans_thrift");

    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span, span))));

    try (KafkaTransport processor = new KafkaTransport(config, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(span, span);
    }
  }

  /** Ensures list encoding works: a json encoded list of spans */
  @Test
  public void messageWithMultipleSpans_json() throws Exception {
    KafkaConfig config = configForTopic("multiple_spans_json");

    producer.send(new KeyedMessage<>(config.topic, Codec.JSON.writeSpans(asList(span, span))));

    try (KafkaTransport processor = new KafkaTransport(config, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(span, span);
    }
  }

  /** Ensures malformed spans don't hang the processor */
  @Test
  public void skipsMalformedData() throws Exception {
    KafkaConfig config = configForTopic("decoder_exception");

    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));
    producer.send(new KeyedMessage<>(config.topic, "[\"='".getBytes())); // screwed up json
    producer.send(new KeyedMessage<>(config.topic, "malformed".getBytes()));
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));

    try (KafkaTransport processor = new KafkaTransport(config, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(span);
      // the only way we could read this, is if the malformed spans were skipped.
      assertThat(recvdSpans.take()).containsExactly(span);
    }
  }

  /** Guards against errors that leak from storage, such as InvalidQueryException */
  @Test
  @Ignore // TODO: figure out why this breaks travis
  public void skipsOnConsumerException() throws Exception {
    KafkaConfig config = configForTopic("consumer_exception");

    consumer = (spans, callback) -> {
      if (recvdSpans.size() == 1) {
        callback.onError(new RuntimeException("storage fell over"));
      } else {
        recvdSpans.add(spans);
        callback.onSuccess(null);
      }
    };

    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span)))); // tossed on error
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));

    try (KafkaTransport processor = new KafkaTransport(config, consumer)) {
      assertThat(recvdSpans.take()).containsExactly(span);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(recvdSpans.take()).containsExactly(span);
    }
  }

  KafkaConfig configForTopic(String topic) {
    return KafkaConfig.builder().zookeeper("127.0.0.1:2181").topic(topic).build();
  }
}
