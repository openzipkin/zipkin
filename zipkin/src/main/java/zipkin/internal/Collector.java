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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import zipkin.collector.CollectorMetrics;
import zipkin.storage.Callback;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static zipkin.internal.Util.checkNotNull;

public abstract class Collector<D, S> {

  protected final Logger logger;
  protected final CollectorMetrics metrics;

  protected Collector(Logger logger, @Nullable CollectorMetrics metrics) {
    this.logger = checkNotNull(logger, "logger");
    this.metrics = metrics == null ? CollectorMetrics.NOOP_METRICS : metrics;
  }

  protected abstract List<S> decodeList(D decoder, byte[] serialized);

  protected abstract boolean isSampled(S s);

  protected abstract void record(List<S> spans, Callback<Void> callback);

  protected abstract String idString(S span);

  boolean shouldWarn() {
    return logger.isLoggable(FINE);
  }

  void warn(String message, Throwable e) {
    logger.log(FINE, message, e);
  }

  protected void acceptSpans(byte[] serializedSpans, D decoder, Callback<Void> callback) {
    metrics.incrementBytes(serializedSpans.length);
    List<S> spans;
    try {
      spans = decodeList(decoder, serializedSpans);
    } catch (RuntimeException e) {
      callback.onError(errorReading(e));
      return;
    }
    accept(spans, callback);
  }

  public void accept(List<S> spans, Callback<Void> callback) {
    if (spans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }
    metrics.incrementSpans(spans.size());

    List<S> sampled = sample(spans);
    if (sampled.isEmpty()) {
      callback.onSuccess(null);
      return;
    }

    try {
      record(sampled, acceptSpansCallback(sampled));
      callback.onSuccess(null);
    } catch (RuntimeException e) {
      callback.onError(errorStoringSpans(sampled, e));
      return;
    }
  }

  List<S> sample(List<S> input) {
    List<S> sampled = new ArrayList<>(input.size());
    for (S s : input) {
      if (isSampled(s)) sampled.add(s);
    }
    int dropped = input.size() - sampled.size();
    if (dropped > 0) metrics.incrementSpansDropped(dropped);
    return sampled;
  }

  Callback<Void> acceptSpansCallback(final List<S> spans) {
    return new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
      }

      @Override public void onError(Throwable t) {
        errorStoringSpans(spans, t);
      }

      @Override
      public String toString() {
        return appendSpanIds(spans, new StringBuilder("AcceptSpans(")).append(")").toString();
      }
    };
  }

  protected RuntimeException errorReading(Throwable e) {
    return errorReading("Cannot decode spans", e);
  }

  RuntimeException errorReading(String message, Throwable e) {
    metrics.incrementMessagesDropped();
    return doError(message, e);
  }

  /**
   * When storing spans, an exception can be raised before or after the fact. This adds context of
   * span ids to give logs more relevance.
   */
  RuntimeException errorStoringSpans(List<S> spans, Throwable e) {
    metrics.incrementSpansDropped(spans.size());
    // The exception could be related to a span being huge. Instead of filling logs,
    // print trace id, span id pairs
    StringBuilder msg = appendSpanIds(spans, new StringBuilder("Cannot store spans "));
    return doError(msg.toString(), e);
  }

  RuntimeException doError(String message, Throwable e) {
    String error = e.getMessage() != null ? e.getMessage() : "";
    if (e instanceof RuntimeException && error.startsWith("Malformed")) {
      if (shouldWarn()) warn(error, e);
      return (RuntimeException) e;
    } else {
      if (shouldWarn()) {
        message = format("%s due to %s(%s)", message, e.getClass().getSimpleName(), error);
        warn(message, e);
      }
      return new RuntimeException(message, e);
    }
  }

  StringBuilder appendSpanIds(List<S> spans, StringBuilder message) {
    message.append("[");
    int i = 0;
    Iterator<S> iterator = spans.iterator();
    while (iterator.hasNext() && i++ < 3) {
      message.append(idString(iterator.next()));
      if (iterator.hasNext()) message.append(", ");
    }
    if (iterator.hasNext()) message.append("...");

    return message.append("]");
  }
}
