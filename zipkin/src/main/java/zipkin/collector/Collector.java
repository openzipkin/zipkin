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
import java.util.List;
import java.util.logging.Logger;
import zipkin.SpanDecoder;
import zipkin.internal.DetectingSpanDecoder;
import zipkin.internal.V2Collector;
import zipkin.internal.V2JsonSpanDecoder;
import zipkin.internal.V2SpanConverter;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import static zipkin.internal.DetectingSpanDecoder.detectFormat;
import static zipkin.internal.Util.checkNotNull;

/**
 * This component takes action on spans received from a transport. This includes deserializing,
 * sampling and scheduling for storage.
 *
 * <p>Callbacks passed do not propagate to the storage layer. They only return success or failures
 * before storage is attempted. This ensures that calling threads are disconnected from storage
 * threads.
 */
public class Collector
  extends zipkin.internal.Collector<SpanDecoder, zipkin.Span> { // not final for mock

  /** Needed to scope this to the correct logging category */
  public static Builder builder(Class<?> loggingClass) {
    return new Builder(Logger.getLogger(checkNotNull(loggingClass, "loggingClass").getName()));
  }

  public static final class Builder {
    final Logger logger;
    StorageComponent storage = null;
    CollectorSampler sampler = null;
    CollectorMetrics metrics = null;

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

  final CollectorSampler sampler;
  final StorageComponent storage;
  final V2Collector storage2;

  Collector(Builder builder) {
    super(builder.logger, builder.metrics);
    this.storage = checkNotNull(builder.storage, "storage");
    this.sampler = builder.sampler == null ? CollectorSampler.ALWAYS_SAMPLE : builder.sampler;
    if (storage instanceof V2StorageComponent) {
      storage2 = new V2Collector(
        builder.logger,
        builder.metrics,
        builder.sampler,
        ((V2StorageComponent) storage).delegate()
      );
    } else {
      storage2 = null;
    }
  }

  @Override
  public void acceptSpans(byte[] serializedSpans, SpanDecoder decoder, Callback<Void> callback) {
    try {
      if (decoder instanceof DetectingSpanDecoder) decoder = detectFormat(serializedSpans);
    } catch (RuntimeException e) {
      metrics.incrementBytes(serializedSpans.length);
      callback.onError(errorReading(e));
      return;
    }
    if (storage2 != null && decoder instanceof V2JsonSpanDecoder) {
      storage2.acceptSpans(serializedSpans, SpanBytesDecoder.JSON_V2, callback);
    } else {
      super.acceptSpans(serializedSpans, decoder, callback);
    }
  }

  /**
   * @deprecated All transports accept encoded lists of spans. Please update reporters to do so.
   */
  @Deprecated public void acceptSpans(List<byte[]> serializedSpans, SpanDecoder decoder,
    Callback<Void> callback) {
    List<zipkin.Span> spans = new ArrayList<>(serializedSpans.size());
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

  @Override public void accept(List<zipkin.Span> spans, Callback<Void> callback) {
    if (storage2 != null) {
      int length = spans.size();
      List<Span> span2s = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        span2s.addAll(V2SpanConverter.fromSpan(spans.get(i)));
      }
      storage2.accept(span2s, callback);
    } else {
      super.accept(spans, callback);
    }
  }

  @Override protected List<zipkin.Span> decodeList(SpanDecoder decoder, byte[] serialized) {
    return decoder.readSpans(serialized);
  }

  @Override protected boolean isSampled(zipkin.Span span) {
    return sampler.isSampled(span.traceId, span.debug);
  }

  @Override protected void record(List<zipkin.Span> sampled, Callback<Void> callback) {
    storage.asyncSpanConsumer().accept(sampled, callback);
  }

  @Override protected String idString(zipkin.Span span) {
    return span.idString();
  }
}
