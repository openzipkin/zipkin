/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.storage.StorageComponent;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static zipkin2.Call.propagateIfFatal;

/**
 * This component takes action on spans received from a transport. This includes deserializing,
 * sampling and scheduling for storage.
 *
 * <p>Callbacks passed do not propagate to the storage layer. They only return success or failures
 * before storage is attempted. This ensures that calling threads are disconnected from storage
 * threads.
 */
public class Collector { // not final for mock
  static final Callback<Void> NOOP_CALLBACK = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  /** Needed to scope this to the correct logging category */
  public static Builder newBuilder(Class<?> loggingClass) {
    if (loggingClass == null) throw new NullPointerException("loggingClass == null");
    return new Builder(Logger.getLogger(loggingClass.getName()));
  }

  public static final class Builder {
    final Logger logger;
    StorageComponent storage;
    CollectorSampler sampler;
    CollectorMetrics metrics;

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

    public Collector build() {
      return new Collector(this);
    }
  }

  final Logger logger;
  final CollectorMetrics metrics;
  final CollectorSampler sampler;
  final StorageComponent storage;

  Collector(Builder builder) {
    if (builder.logger == null) throw new NullPointerException("logger == null");
    this.logger = builder.logger;
    this.metrics = builder.metrics == null ? CollectorMetrics.NOOP_METRICS : builder.metrics;
    if (builder.storage == null) throw new NullPointerException("storage == null");
    this.storage = builder.storage;
    this.sampler = builder.sampler == null ? CollectorSampler.ALWAYS_SAMPLE : builder.sampler;
  }

  public void accept(List<Span> spans, Callback<Void> callback) {
    accept(spans, callback, Runnable::run);
  }

  /**
   * @param executor the executor used to enqueue the storage request.
   *
   * <p>Calls to get the storage component could be blocking. This ensures requests that block
   * callers (such as http or gRPC) do not add additional load during such events.
   */
  public void accept(List<Span> spans, Callback<Void> callback, Executor executor) {
    if (spans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }
    metrics.incrementSpans(spans.size());

    List<Span> sampledSpans = sample(spans);
    if (sampledSpans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }

    // In order to ensure callers are not blocked, we swap callbacks when we get to the storage
    // phase of this process. Here, we create a callback whose sole purpose is classifying later
    // errors on this bundle of spans in the same log category. This allows people to only turn on
    // debug logging in one place.
    try {
      executor.execute(new StoreSpans(sampledSpans));
      callback.onSuccess(null);
    } catch (Throwable unexpected) { // ensure if a future is supplied we always set value or error
      callback.onError(unexpected);
      throw unexpected;
    }
  }

  /** Like {@link #acceptSpans(byte[], BytesDecoder, Callback)}, except using a byte buffer. */
  public void acceptSpans(ByteBuffer encoded, SpanBytesDecoder decoder, Callback<Void> callback,
    Executor executor) {
    List<Span> spans;
    try {
      spans = decoder.decodeList(encoded);
    } catch (RuntimeException | Error e) {
      handleDecodeError(e, callback);
      return;
    }
    accept(spans, callback, executor);
  }

  /**
   * Before calling this, call {@link CollectorMetrics#incrementMessages()}, and {@link
   * CollectorMetrics#incrementBytes(int)}. Do not call any other metrics callbacks as those are
   * handled internal to this method.
   *
   * @param serialized not empty message
   */
  public void acceptSpans(byte[] serialized, Callback<Void> callback) {
    BytesDecoder<Span> decoder;
    try {
      decoder = SpanBytesDecoderDetector.decoderForListMessage(serialized);
    } catch (RuntimeException | Error e) {
      handleDecodeError(e, callback);
      return;
    }
    acceptSpans(serialized, decoder, callback);
  }

  /**
   * Before calling this, call {@link CollectorMetrics#incrementMessages()}, and {@link
   * CollectorMetrics#incrementBytes(int)}. Do not call any other metrics callbacks as those are
   * handled internal to this method.
   *
   * @param serializedSpans not empty message
   */
  public void acceptSpans(
    byte[] serializedSpans, BytesDecoder<Span> decoder, Callback<Void> callback) {
    List<Span> spans;
    try {
      spans = decodeList(decoder, serializedSpans);
    } catch (RuntimeException | Error e) {
      handleDecodeError(e, callback);
      return;
    }
    accept(spans, callback);
  }

  List<Span> decodeList(BytesDecoder<Span> decoder, byte[] serialized) {
    List<Span> out = new ArrayList<>();
    decoder.decodeList(serialized, out);
    return out;
  }

  void store(List<Span> sampledSpans, Callback<Void> callback) {
    storage.spanConsumer().accept(sampledSpans).enqueue(callback);
  }

  String idString(Span span) {
    return span.traceId() + "/" + span.id();
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

  class StoreSpans implements Callback<Void>, Runnable {
    final List<Span> spans;

    StoreSpans(List<Span> spans) {
      this.spans = spans;
    }

    @Override public void run() {
      try {
        store(spans, this);
      } catch (RuntimeException | Error e) {
        // While unexpected, invoking the storage command could raise an error synchronously. When
        // that's the case, we wouldn't have invoked callback.onSuccess, so we need to handle the
        // error here.
        onError(e);
      }
    }

    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
      handleStorageError(spans, t, NOOP_CALLBACK);
    }

    @Override public String toString() {
      return appendSpanIds(spans, new StringBuilder("StoreSpans(")) + ")";
    }
  }

  void handleDecodeError(Throwable e, Callback<Void> callback) {
    metrics.incrementMessagesDropped();
    handleError(e, "Cannot decode spans"::toString, callback);
  }

  /**
   * When storing spans, an exception can be raised before or after the fact. This adds context of
   * span ids to give logs more relevance.
   */
  void handleStorageError(List<Span> spans, Throwable e, Callback<Void> callback) {
    metrics.incrementSpansDropped(spans.size());
    // The exception could be related to a span being huge. Instead of filling logs,
    // print trace id, span id pairs
    handleError(e, () -> appendSpanIds(spans, new StringBuilder("Cannot store spans ")), callback);
  }

  void handleError(Throwable e, Supplier<String> defaultLogMessage, Callback<Void> callback) {
    propagateIfFatal(e);
    callback.onError(e);
    if (!logger.isLoggable(FINE)) return;

    String error = e.getMessage() != null ? e.getMessage() : "";
    // We have specific code that customizes log messages. Use this when the case.
    if (error.startsWith("Malformed") || error.startsWith("Truncated")) {
      logger.log(FINE, error, e);
    } else { // otherwise, beautify the message
      String message =
        format("%s due to %s(%s)", defaultLogMessage.get(), e.getClass().getSimpleName(), error);
      logger.log(FINE, message, e);
    }
  }

  // TODO: this logic needs to be redone as service names are more important than span IDs. Also,
  // span IDs repeat between client and server!
  String appendSpanIds(List<Span> spans, StringBuilder message) {
    message.append("[");
    int i = 0;
    Iterator<Span> iterator = spans.iterator();
    while (iterator.hasNext() && i++ < 3) {
      message.append(idString(iterator.next()));
      if (iterator.hasNext()) message.append(", ");
    }
    if (iterator.hasNext()) message.append("...");

    return message.append("]").toString();
  }
}
