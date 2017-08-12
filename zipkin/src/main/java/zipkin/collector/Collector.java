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
package zipkin.collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.internal.Span2JsonDecoder;
import zipkin.internal.Util;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static java.lang.String.format;
import static java.util.logging.Level.WARNING;
import static zipkin.internal.Util.checkNotNull;

/**
 * This component takes action on spans received from a transport. This includes deserializing,
 * sampling and scheduling for storage.
 *
 * <p>Callbacks passed do not propagate to the storage layer. They only return success or failures
 * before storage is attempted. This ensures that calling threads are disconnected from storage
 * threads.
 */
public final class Collector {

  /** Needed to scope this to the correct logging category */
  public static Builder builder(Class<?> loggingClass) {
    return new Builder(Logger.getLogger(checkNotNull(loggingClass, "loggingClass").getName()));
  }

  public static final class Builder {
    final Logger logger;
    StorageComponent storage = null;
    CollectorSampler sampler = CollectorSampler.ALWAYS_SAMPLE;
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;

    Builder(Logger logger) {
      this.logger = logger;
    }

    /** @see {@link CollectorComponent.Builder#storage(StorageComponent)} */
    public Builder storage(StorageComponent storage) {
      this.storage = checkNotNull(storage, "storage");
      return this;
    }

    /** @see {@link CollectorComponent.Builder#metrics(CollectorMetrics)} */
    public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics");
      return this;
    }

    /** @see {@link CollectorComponent.Builder#sampler(CollectorSampler)} */
    public Builder sampler(CollectorSampler sampler) {
      this.sampler = checkNotNull(sampler, "sampler");
      return this;
    }

    public Collector build() {
      return new Collector(this);
    }
  }

  final Logger logger;
  final StorageComponent storage;
  final CollectorSampler sampler;
  final CollectorMetrics metrics;

  Collector(Builder builder) {
    this.logger = checkNotNull(builder.logger, "logger");
    this.storage = checkNotNull(builder.storage, "storage");
    this.sampler = builder.sampler == null ? CollectorSampler.ALWAYS_SAMPLE : builder.sampler;
    this.metrics = builder.metrics == null ? CollectorMetrics.NOOP_METRICS : builder.metrics;
  }

  /** zipkin v2 will have this tag, and others won't. */
  static final byte[] LOCAL_ENDPOINT_TAG = "\"localEndpoint\"".getBytes(Util.UTF_8);
  static final SpanDecoder JSON2_DECODER = new Span2JsonDecoder();

  public void acceptSpans(byte[] bytes, Callback<Void> callback) {
    // In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
    // .. If the first byte isn't in that range, it isn't a thrift.
    //
    // When byte(0) == '[' (91), assume it is a list of json-encoded spans
    //
    // When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
    // .. When serializing a Span (Struct), the first byte will be the type of a field
    // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
    // .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
    if (bytes[0] == '[') {
      bytes: // searches for a substring matching zipkin v2 format. otherwise assume it isn't.
      for (int i = 0; i < bytes.length - LOCAL_ENDPOINT_TAG.length + 1; i++) {
        for (int j = 0; j < LOCAL_ENDPOINT_TAG.length; j++) {
          if (bytes[i + j] != LOCAL_ENDPOINT_TAG[j]) {
            continue bytes;
          }
        }
        acceptSpans(bytes, JSON2_DECODER, callback);
        return;
      }
      acceptSpans(bytes, SpanDecoder.JSON_DECODER, callback);
    } else {
      if (bytes[0] == 12 /* TType.STRUCT */) {
        acceptSpans(bytes, SpanDecoder.THRIFT_DECODER, callback);
      } else {
        acceptSpans(Collections.singletonList(bytes), SpanDecoder.THRIFT_DECODER, callback);
      }
    }
  }

  public void acceptSpans(byte[] serializedSpans, SpanDecoder decoder, Callback<Void> callback) {
    metrics.incrementBytes(serializedSpans.length);
    List<Span> spans;
    try {
      spans = decoder.readSpans(serializedSpans);
    } catch (RuntimeException e) {
      callback.onError(errorReading(e));
      return;
    }
    accept(spans, callback);
  }

  public void acceptSpans(List<byte[]> serializedSpans, SpanDecoder decoder,
    Callback<Void> callback) {
    List<Span> spans = new ArrayList<>(serializedSpans.size());
    try {
      int bytesRead = 0;
      for (byte[] serializedSpan : serializedSpans) {
        bytesRead += serializedSpan.length;
        spans.add(decoder.readSpan(serializedSpan));
      }
      metrics.incrementBytes(bytesRead);
    } catch (RuntimeException e) {
      callback.onError(errorReading(e));
      return;
    }
    accept(spans, callback);
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
      storage.asyncSpanConsumer().accept(sampled, acceptSpansCallback(sampled));
      callback.onSuccess(null);
    } catch (RuntimeException e) {
      callback.onError(errorStoringSpans(sampled, e));
      return;
    }
  }

  List<Span> sample(List<Span> input) {
    List<Span> sampled = new ArrayList<>(input.size());
    for (Span s : input) {
      if (sampler.isSampled(s)) sampled.add(s);
    }
    int dropped = input.size() - sampled.size();
    if (dropped > 0) metrics.incrementSpansDropped(dropped);
    return sampled;
  }

  Callback<Void> acceptSpansCallback(final List<Span> spans) {
    return new Callback<Void>() {
      @Override public void onSuccess(Void value) {
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
    if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage()
        .startsWith("Malformed")) {
      logger.log(WARNING, e.getMessage(), e);
      return (RuntimeException) e;
    } else {
      message = format("%s due to %s(%s)", message, e.getClass().getSimpleName(),
          e.getMessage() == null ? "" : e.getMessage());
      logger.log(WARNING, message, e);
      return new RuntimeException(message, e);
    }
  }

  static StringBuilder appendSpanIds(List<Span> spans, StringBuilder message) {
    message.append("[");
    for (Iterator<Span> iterator = spans.iterator(); iterator.hasNext(); ) {
      message.append(iterator.next().idString());
      if (iterator.hasNext()) message.append(", ");
    }
    return message.append("]");
  }
}
