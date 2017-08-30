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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;

import static zipkin.SpanDecoder.DETECTING_DECODER;
import static zipkin.storage.Callback.NOOP;

/**
 * Consumes spans from messages on a RabbitMQ queue. Malformed messages will be discarded. Errors in
 * the storage component will similarly be ignored, with no retry of the message.
 */
class RabbitMqSpanConsumer extends DefaultConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(RabbitMqSpanConsumer.class);

  private final Collector collector;
  private final CollectorMetrics metrics;

  RabbitMqSpanConsumer(RabbitMqCollector.Builder builder, Channel channel) {
    super(channel);

    this.collector = builder.delegate.build();
    this.metrics = builder.metrics;
  }

  @Override
  public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
    byte[] body) throws IOException {
    try {
      this.metrics.incrementMessages();
      // If we received legacy single-span encoding, decode it into a singleton list
      if (body[0] <= 16 && body[0] != 12 /* thrift, but not a list */) {
        this.metrics.incrementBytes(body.length);
        Span span = SpanDecoder.THRIFT_DECODER.readSpan(body);
        this.collector.accept(Collections.singletonList(span), NOOP);
      } else {
        this.collector.acceptSpans(body, DETECTING_DECODER, NOOP);
      }
    } catch (RuntimeException e) {
      this.metrics.incrementMessagesDropped();
      LOG.debug("Exception while collecting message from RabbitMQ", e);
    }
  }
}
