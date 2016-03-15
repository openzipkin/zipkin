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

import com.github.charithe.kafka.KafkaJunitRule;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.SERVER_RECV;

public class KafkaTransportTest {
  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule();

  Endpoint endpoint = Endpoint.create("web", 127 << 24 | 1, 80);
  Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, endpoint);
  Span span = new Span.Builder().id(1L).traceId(1L).timestamp(ann.timestamp).name("get")
      .addAnnotation(ann).build();

  /** Ensures legacy encoding works: a single TBinaryProtocol encoded span */
  @Test
  public void messageWithSingleSpan() throws Exception {
    KafkaConfig config = KafkaConfig.builder()
        .zookeeper(kafka.zookeeperConnectionString())
        .topic("single_span").build();

    CompletableFuture<List<Span>> promise = new CompletableFuture<>();

    Producer<String, byte[]> producer = new Producer<>(kafka.producerConfigWithDefaultEncoder());
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpan(span)));
    producer.close();

    try (KafkaTransport processor = new KafkaTransport(config, promise::complete)) {
      assertThat(promise.get()).containsOnly(span);
    }
  }

  /** Ensures list encoding works: a TBinaryProtocol encoded list of spans */
  @Test
  public void messageWithMultipleSpans() throws Exception {
    KafkaConfig config = KafkaConfig.builder()
        .zookeeper(kafka.zookeeperConnectionString())
        .topic("multiple_spans").build();

    CompletableFuture<List<Span>> promise = new CompletableFuture<>();

    Producer<String, byte[]> producer = new Producer<>(kafka.producerConfigWithDefaultEncoder());
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span, span))));
    producer.close();

    try (KafkaTransport processor = new KafkaTransport(config, promise::complete)) {
      assertThat(promise.get()).containsExactly(span, span);
    }
  }

  /** Ensures malformed spans don't hang the processor */
  @Test
  public void skipsMalformedData() throws Exception {
    KafkaConfig config = KafkaConfig.builder()
        .zookeeper(kafka.zookeeperConnectionString())
        .topic("malformed").build();

    LinkedBlockingQueue<List<Span>> recvdSpans = new LinkedBlockingQueue<>();

    Producer<String, byte[]> producer = new Producer<>(kafka.producerConfigWithDefaultEncoder());
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));
    producer.send(new KeyedMessage<>(config.topic, "malformed".getBytes()));
    producer.send(new KeyedMessage<>(config.topic, Codec.THRIFT.writeSpans(asList(span))));
    producer.close();

    try (KafkaTransport processor = new KafkaTransport(config, recvdSpans::add)) {
      assertThat(recvdSpans.take()).containsExactly(span);
      // the only way we could read this, is if the malformed span was skipped.
      assertThat(recvdSpans.take()).containsExactly(span);
    }
  }
}
