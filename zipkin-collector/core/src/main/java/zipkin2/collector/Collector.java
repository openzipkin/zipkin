/*
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
package zipkin2.collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;
import zipkin2.collector.filter.SpanFilter;
import zipkin2.storage.StorageComponent;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;

/**
 * This component takes action on spans received from a transport. This includes deserializing,
 * sampling and scheduling for storage.
 *
 * <p>Callbacks passed do not propagate to the storage layer. They only return success or failures
 * before storage is attempted. This ensures that calling threads are disconnected from storage
 * threads.
 */
public class Collector { // not final for mock

  /** Needed to scope this to the correct logging category */
  public static Builder newBuilder(Class<?> loggingClass) {
    if (loggingClass == null) throw new NullPointerException("loggingClass == null");
    return new Builder(Logger.getLogger(loggingClass.getName()));
  }

  public static final class Builder {
    final Logger logger;
    StorageComponent storage = null;
    CollectorSampler sampler = null;
    CollectorMetrics metrics = null;
    List<SpanFilter> filters = null;

    Builder(Logger logger) {
      this.logger = logger;
    }

    /** @see {@link CollectorComponent.Builder#storage(StorageComponent)} */
    public Builder storage(StorageComponent storage) {
      if (storage == null) throw new NullPointerException("storage == null");
      this.storage = storage;
      return this;
    }

    /** @see {@link CollectorComponent.Builder#metrics(CollectorMetrics)} */
    public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics;
      return this;
    }

    /** @see {@link CollectorComponent.Builder#sampler(CollectorSampler)} */
    public Builder sampler(CollectorSampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      this.sampler = sampler;
      return this;
    }

    /** @see {@link CollectorComponent.Builder#filters(List<SpanFilter)} */
    public Builder filters(List<SpanFilter> filters) {
      if (filters == null) throw new NullPointerException("filters == null");
      this.filters = filters;
      return this;    }

    public Collector build() {
      return new Collector(this);
    }

  }

  final Logger logger;
  final CollectorMetrics metrics;
  final CollectorSampler sampler;
  final StorageComponent storage;
  final List<SpanFilter> filters;

  Collector(Builder builder) {
    if (builder.logger == null) throw new NullPointerException("logger == null");
    this.logger = builder.logger;
    this.metrics = builder.metrics == null ? CollectorMetrics.NOOP_METRICS : builder.metrics;
    if (builder.storage == null) throw new NullPointerException("storage == null");
    this.storage = builder.storage;
    this.sampler = builder.sampler == null ? CollectorSampler.ALWAYS_SAMPLE : builder.sampler;
    this.filters = builder.filters;
  }

  public void accept(List<Span> spans, Callback<Void> callback) {
    if (spans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }
    metrics.incrementSpans(spans.size());

    List<Span> sampled = sample(spans);
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

  public void acceptSpans(byte[] serialized, Callback<Void> callback) {
    BytesDecoder<Span> decoder;
    try {
      decoder = SpanBytesDecoderDetector.decoderForListMessage(serialized);
    } catch (RuntimeException e) {
      metrics.incrementBytes(serialized.length);
      callback.onError(errorReading(e));
      return;
    }
    acceptSpans(serialized, decoder, callback);
  }

  public void acceptSpans(
      byte[] serializedSpans, BytesDecoder<Span> decoder, Callback<Void> callback) {
    metrics.incrementBytes(serializedSpans.length);
    List<Span> spans;
    try {
      spans = decodeList(decoder, serializedSpans);
      spans = filterSpans(spans, callback);
    } catch (RuntimeException e) {
      callback.onError(errorReading(e));
      return;
    }
    accept(spans, callback);
  }

  List<Span> decodeList(BytesDecoder<Span> decoder, byte[] serialized) {
    List<Span> out = new ArrayList<>();
    if (!decoder.decodeList(serialized, out)) return Collections.emptyList();
    return out;
  }

  /**
   * Take the list of spans and pump them through pre-configured filters
   *
   * @param spans
   */
  List<Span> filterSpans(List<Span> spans, Callback<Void> callback) {
    List<Span> processed = spans;
    if (filters == null) {
      return spans;
    }
    for (SpanFilter filter : filters) {
      processed = filter.process(processed, metrics, callback);
    }
    return processed;
  }

  void record(List<Span> sampled, Callback<Void> callback) {
    storage.spanConsumer().accept(sampled).enqueue(callback);
  }

  String idString(Span span) {
    return span.traceId() + "/" + span.id();
  }

  boolean shouldWarn() {
    return logger.isLoggable(FINE);
  }

  void warn(String message, Throwable e) {
    logger.log(FINE, message, e);
  }

  List<Span> sample(List<Span> input) {
    List<Span> sampled = new ArrayList<>(input.size());
    for (int i = 0, length = input.size(); i < length; i++) {
      Span s = input.get(i);
      if (sampler.isSampled(s.traceId(), Boolean.TRUE.equals(s.debug()))) {
        sampled.add(s);
      }
    }
    int dropped = input.size() - sampled.size();
    if (dropped > 0) metrics.incrementSpansDropped(dropped);
    return sampled;
  }

  Callback<Void> acceptSpansCallback(final List<Span> spans) {
    return new Callback<Void>() {
      @Override
      public void onSuccess(Void value) {}

      @Override
      public void onError(Throwable t) {
        errorStoringSpans(spans, t);
      }

      @Override
      public String toString() {
        return appendSpanIds(spans, new StringBuilder("AcceptSpans(")).append(")").toString();
      }
    };
  }

  RuntimeException errorReading(Throwable e) {
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
  RuntimeException errorStoringSpans(List<Span> spans, Throwable e) {
    metrics.incrementSpansDropped(spans.size());
    // The exception could be related to a span being huge. Instead of filling logs,
    // print trace id, span id pairs
    StringBuilder msg = appendSpanIds(spans, new StringBuilder("Cannot store spans "));
    return doError(msg.toString(), e);
  }

  RuntimeException doError(String message, Throwable e) {
    String error = e.getMessage() != null ? e.getMessage() : "";
    if (e instanceof RuntimeException
        && (error.startsWith("Malformed") || error.startsWith("Truncated"))) {
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

  StringBuilder appendSpanIds(List<Span> spans, StringBuilder message) {
    message.append("[");
    int i = 0;
    Iterator<Span> iterator = spans.iterator();
    while (iterator.hasNext() && i++ < 3) {
      message.append(idString(iterator.next()));
      if (iterator.hasNext()) message.append(", ");
    }
    if (iterator.hasNext()) message.append("...");

    return message.append("]");
  }
}
