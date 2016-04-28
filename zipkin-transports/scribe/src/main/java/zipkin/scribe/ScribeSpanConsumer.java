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
package zipkin.scribe;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import zipkin.AsyncSpanConsumer;
import zipkin.Callback;
import zipkin.Codec;
import zipkin.CollectorMetrics;
import zipkin.Span;
import zipkin.internal.Lazy;
import zipkin.internal.SpanConsumerLogger;

final class ScribeSpanConsumer implements Scribe {
  final String category;
  final Lazy<AsyncSpanConsumer> consumer;
  final SpanConsumerLogger logger;

  ScribeSpanConsumer(String category, Lazy<AsyncSpanConsumer> consumer, CollectorMetrics metrics) {
    this.category = category;
    this.consumer = consumer;
    this.logger = new SpanConsumerLogger(ScribeSpanConsumer.class, metrics);
  }

  @Override
  public ListenableFuture<ResultCode> log(List<LogEntry> messages) {
    logger.acceptedMessage();
    AtomicInteger serializedBytes = new AtomicInteger(); // because of the lambda
    List<Span> spans;
    try {
      spans = messages.stream()
          .filter(m -> m.category.equals(category))
          .map(m -> m.message.getBytes(StandardCharsets.ISO_8859_1))
          .map(b -> Base64.getMimeDecoder().decode(b)) // finagle-zipkin uses mime encoding
          .peek(b -> serializedBytes.addAndGet(b.length))
          .map(Codec.THRIFT::readSpan)
          .filter(s -> s != null).collect(Collectors.toList());
    } catch (RuntimeException e) {
      logger.errorReading(e);
      return Futures.immediateFailedFuture(e);
    }
    logger.readBytes(serializedBytes.get());

    if (spans.isEmpty()) return Futures.immediateFuture(ResultCode.OK);
    logger.readSpans(spans.size());

    ErrorLoggingFuture result = new ErrorLoggingFuture(logger, spans);
    try {
      consumer.get().accept(spans, result);
    } catch (RuntimeException e) {
      result.onError(e);
    }
    return result;
  }

  static final class ErrorLoggingFuture extends AbstractFuture<ResultCode>
      implements Callback<Void> {
    final SpanConsumerLogger logger;
    final List<Span> spans;

    ErrorLoggingFuture(SpanConsumerLogger logger, List<Span> spans) {
      this.logger = logger;
      this.spans = spans;
    }

    @Override public void onSuccess(Void value) {
      set(ResultCode.OK);
    }

    @Override public void onError(Throwable t) {
      logger.errorAcceptingSpans(spans, t);
      setException(t);
    }
  }
}
