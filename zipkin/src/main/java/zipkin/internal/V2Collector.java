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
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.storage.Callback;
import zipkin2.Span;
import zipkin2.codec.BytesDecoder;
import zipkin2.storage.StorageComponent;

import static zipkin.internal.Util.checkNotNull;

public final class V2Collector extends Collector<BytesDecoder<Span>, Span> {
  final StorageComponent storage;
  final CollectorSampler sampler;

  public V2Collector(Logger logger, @Nullable CollectorMetrics metrics,
    @Nullable CollectorSampler sampler, StorageComponent storage) {
    super(logger, metrics);
    this.storage = checkNotNull(storage, "storage");
    this.sampler = sampler == null ? CollectorSampler.ALWAYS_SAMPLE : sampler;
  }

  @Override
  public void acceptSpans(byte[] serializedSpans, BytesDecoder<Span> decoder,
    Callback<Void> callback) {
    super.acceptSpans(serializedSpans, decoder, callback);
  }

  @Override protected List<Span> decodeList(BytesDecoder<Span> decoder, byte[] serialized) {
    List<Span> out = new ArrayList<>();
    if (!decoder.decodeList(serialized, out)) return Collections.emptyList();
    return out;
  }

  @Override protected boolean isSampled(Span span) {
    return sampler.isSampled(Util.lowerHexToUnsignedLong(span.traceId()), span.debug());
  }

  @Override protected void record(List<Span> sampled, Callback<Void> callback) {
    storage.spanConsumer().accept(sampled).enqueue(new V2CallbackAdapter<>(callback));
  }

  @Override protected String idString(Span span) {
    return span.traceId() + "/" + span.id();
  }
}
