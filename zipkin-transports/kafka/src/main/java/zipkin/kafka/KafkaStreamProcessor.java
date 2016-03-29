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

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import zipkin.Span;
import zipkin.SpanConsumer;

import static java.util.logging.Level.WARNING;

final class KafkaStreamProcessor implements Runnable {
  final Logger logger = Logger.getLogger(KafkaStreamProcessor.class.getName());
  final KafkaStream<String, List<Span>> stream;
  final SpanConsumer spanConsumer;

  KafkaStreamProcessor(KafkaStream<String, List<Span>> stream, SpanConsumer spanConsumer) {
    this.stream = stream;
    this.spanConsumer = spanConsumer;
  }

  @Override
  public void run() {
    ConsumerIterator<String, List<Span>> messages = stream.iterator();
    while (messages.hasNext()) {
      List<Span> spans = messages.next().message();
      if (spans.isEmpty()) continue;
      try {
        spanConsumer.accept(spans);
      } catch (RuntimeException e) {
        // The exception could be related to a span being huge. Instead of filling logs,
        // print trace id, span id pairs
        StringBuilder message = new StringBuilder("unhandled error processing traceId -> spanId: ");
        for (Iterator<Span> iterator = spans.iterator(); iterator.hasNext(); ) {
          Span span = iterator.next();
          message.append(span.traceId).append(" -> ").append(span.id);
          if (iterator.hasNext()) message.append(",");
        }
        logger.log(WARNING, message.toString(), e);
      }
    }
  }
}
