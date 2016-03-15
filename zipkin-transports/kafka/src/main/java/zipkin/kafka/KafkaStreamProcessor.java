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
import zipkin.Span;
import zipkin.SpanConsumer;

final class KafkaStreamProcessor implements Runnable {

  private final KafkaStream<String, List<Span>> stream;
  private final SpanConsumer spanConsumer;

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
      spanConsumer.accept(spans);
    }
  }
}
