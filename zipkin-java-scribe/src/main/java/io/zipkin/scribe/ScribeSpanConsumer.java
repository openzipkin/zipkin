/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.scribe;

import io.zipkin.Codec;
import io.zipkin.Span;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ScribeSpanConsumer implements Scribe {

  private final Consumer<List<Span>> consumer;
  private final Codec spanCodec = Codec.THRIFT;

  public ScribeSpanConsumer(Consumer<List<Span>> consumer) {
    this.consumer = consumer;
  }

  @Override
  public ResultCode log(List<LogEntry> messages) {
    Stream<Span> spansToStore = messages.stream()
        .filter(m -> m.category.equals("zipkin"))
        .map(e -> Base64.getMimeDecoder().decode(e.message)) // finagle-zipkin uses mime encoding
        .map(spanCodec::readSpan)
        .filter(s -> s != null);
    consumer.accept(spansToStore.collect(Collectors.toList()));
    return ResultCode.OK;
  }
}
