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
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import zipkin.AsyncSpanConsumer;
import zipkin.Span;
import zipkin.internal.SpanConsumerLogger;

final class KafkaStreamProcessor implements Runnable {
  final SpanConsumerLogger logger = new SpanConsumerLogger(KafkaStreamProcessor.class);
  final KafkaStream<String, List<Span>> stream;
  final AsyncSpanConsumer spanConsumer;

  KafkaStreamProcessor(KafkaStream<String, List<Span>> stream, AsyncSpanConsumer spanConsumer) {
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
        spanConsumer.accept(spans, logger.acceptSpansCallback(spans));
      } catch (RuntimeException e) {
        logger.errorAcceptingSpans(spans, e);
      }
    }
  }
}
