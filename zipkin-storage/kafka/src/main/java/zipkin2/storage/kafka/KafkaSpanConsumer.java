/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

package zipkin2.storage.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.SpanConsumer;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class KafkaSpanConsumer implements SpanConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaSpanStore.class);

    private final KafkaProducer<byte[], byte[]> kafkaProducer;
    private final String topic;
    private final Encoding encoding;

    public KafkaSpanConsumer(KafkaStorage kafkaStorage) {
      kafkaProducer = kafkaStorage.kafkaProducer();
      topic = kafkaStorage.topic();
      encoding = kafkaStorage.encoding();
    }

    @Override
    public Call<Void> accept(List<Span> spanList) {
      LOG.debug("Storing {} spans into kafka", spanList.size());
      byte[] messages;
      if (encoding.equals(Encoding.JSON)) {
        messages = SpanBytesEncoder.JSON_V2.encodeList(spanList);
      } else {
        LOG.error("Unsupported encoding for kafka storage component: {}", encoding);
        return null; // TODO: Is return null a good idea?
      }
      Future<RecordMetadata> sent = kafkaProducer.send(new ProducerRecord<>(topic, messages));
      try {
        sent.get();
        return Call.create(null); // TODO: Do we need to more here?
      } catch (InterruptedException | ExecutionException e) {
        LOG.error("Failed to send spans to kafka", e);
      }

      return null;
    }

}
