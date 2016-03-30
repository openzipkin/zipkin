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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.serializer.StringDecoder;
import zipkin.async.AsyncSpanConsumer;

import static kafka.consumer.Consumer.createJavaConsumerConnector;

/**
 * This transport polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link AsyncSpanConsumer#accept span consumer}.
 */
public final class KafkaTransport implements AutoCloseable {

  final ExecutorService pool;

  public KafkaTransport(KafkaConfig config, AsyncSpanConsumer spanConsumer) {
    this.pool = config.streams == 1
        ? Executors.newSingleThreadExecutor()
        : Executors.newFixedThreadPool(config.streams);
    ConsumerConnector connector = createJavaConsumerConnector(config.forConsumer());

    Map<String, Integer> topicCountMap = new LinkedHashMap<>(1);
    topicCountMap.put(config.topic, config.streams);

    connector.createMessageStreams(topicCountMap, new StringDecoder(null), new SpansDecoder())
        .get(config.topic).forEach(stream ->
        pool.execute(new KafkaStreamProcessor(stream, spanConsumer))
    );
  }

  @Override
  public void close() {
    pool.shutdown();
  }
}
