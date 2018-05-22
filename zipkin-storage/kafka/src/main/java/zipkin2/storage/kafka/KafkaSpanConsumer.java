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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.SpanConsumer;

import java.util.Collections;
import java.util.List;

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
      if (spanList.size() > 0) {
        LOG.debug("Id of first span: {}", spanList.get(0).id());
      }

      // We have to send one span at a time because when spans are received in ground they are usually larger than 1MB
      for (Span span : spanList) {
        byte[] message;
        // TODO: Utest coverage to make sure if `Encoding` is added we fail if this isn't updated
        switch (encoding) {
          case JSON:
            message = SpanBytesEncoder.JSON_V2.encodeList(Collections.singletonList(span));
            break;
          case PROTO3:
            message = SpanBytesEncoder.PROTO3.encodeList(Collections.singletonList(span));
            break;
          default:
            throw new RuntimeException("Unknown encoding for kafka storage component: " + encoding);
        }
        kafkaProducer.send(new ProducerRecord<>(topic, message), (metadata, e) -> {
          if(e == null) {
            LOG.debug("The offset of the record we just sent is: {}", metadata.offset());
          } else {
            // TODO: What is the best way to handle errors?
            LOG.error("Hit exception trying to send " + spanList.size() + " span(s)", e);
            Call.propagateIfFatal(e);
          }
        });
      }
      return Call.create(null); // TODO: Do we need to more here?
    }

}
