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
package zipkin.internal;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import zipkin.Callback;
import zipkin.CollectorMetrics;
import zipkin.Span;

import static java.lang.String.format;
import static java.util.logging.Level.WARNING;
import static zipkin.internal.Util.checkNotNull;

/**
 * Common logging patterns for span consumption lead to a consistent troubleshooting experience,
 * despite transport diversity.
 */
// internal until drift stops
public final class SpanConsumerLogger {
  private final Logger logger;
  private final CollectorMetrics metrics;

  public SpanConsumerLogger(Class<?> clazz, CollectorMetrics metrics) {
    this.logger = Logger.getLogger(checkNotNull(clazz, "class").getName());
    this.metrics = checkNotNull(metrics, "metrics");
  }

  // Visible for testing
  SpanConsumerLogger(Logger logger, CollectorMetrics metrics) {
    this.logger = checkNotNull(logger, "logger");
    this.metrics = checkNotNull(metrics, "metrics");
  }

  /** @see CollectorMetrics#incrementMessages() */
  public void acceptedMessage() {
    metrics.incrementMessages();
  }

  /** @see CollectorMetrics#incrementMessagesDropped() () */
  public void dropMessage() {
    metrics.incrementMessagesDropped();
  }

  /** @see CollectorMetrics#incrementBytes(int) */
  public void readBytes(int count) {
    metrics.incrementBytes(count);
  }

  /** @see CollectorMetrics#incrementSpans(int) */
  public void readSpans(int count) {
    metrics.incrementSpans(count);
  }

  public String errorReading(Throwable e) {
    return errorReading("Cannot decode spans", e);
  }

  public String errorReading(String message, Throwable e) {
    metrics.incrementMessagesDropped();
    return doError(message, e);
  }

  /**
   * When storing spans, an exception can be raised before or after the fact. This adds context of
   * span ids to give logs more relevance.
   */
  public String errorAcceptingSpans(List<Span> spans, Throwable e) {
    metrics.incrementSpansDropped(spans.size());
    // The exception could be related to a span being huge. Instead of filling logs,
    // print trace id, span id pairs
    StringBuilder msg = appendSpanIds(spans, new StringBuilder("Cannot store traceId -> spanId "));
    return doError(msg.toString(), e);
  }

  public Callback<Void> acceptSpansCallback(final List<Span> spans) {
    return new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
      }

      @Override public void onError(Throwable t) {
        errorAcceptingSpans(spans, t);
      }

      @Override
      public String toString() {
        return appendSpanIds(spans, new StringBuilder("AcceptSpans(")).append(")").toString();
      }
    };
  }

  String doError(String message, Throwable e) {
    message = format("%s due to %s(%s)", message, e.getClass().getSimpleName(),
        e.getMessage() == null ? "" : e.getMessage());
    logger.log(WARNING, message, e);
    return message;
  }

  static StringBuilder appendSpanIds(List<Span> spans, StringBuilder message) {
    message.append("[");
    for (Iterator<Span> iterator = spans.iterator(); iterator.hasNext(); ) {
      Span span = iterator.next();
      message.append(format("%016x", span.traceId)).append(" -> ").append(format("%016x", span.id));
      if (iterator.hasNext()) message.append(", ");
    }
    return message.append("]");
  }
}