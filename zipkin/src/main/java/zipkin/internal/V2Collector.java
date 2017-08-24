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

import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.codec.Decoder;
import zipkin.storage.Callback;

import static zipkin.internal.Util.checkNotNull;

public final class V2Collector extends Collector<Decoder<Span>, Span> {
  final V2StorageComponent storage;
  final CollectorSampler sampler;

  public V2Collector(Logger logger, @Nullable CollectorMetrics metrics,
    @Nullable CollectorSampler sampler, V2StorageComponent storage) {
    super(logger, metrics);
    this.storage = checkNotNull(storage, "storage");
    this.sampler = sampler == null ? CollectorSampler.ALWAYS_SAMPLE : sampler;
  }

  @Override
  public void acceptSpans(byte[] serializedSpans, Decoder<Span> decoder, Callback<Void> callback) {
    super.acceptSpans(serializedSpans, decoder, callback);
  }

  @Override protected List<Span> decodeList(Decoder<Span> decoder, byte[] serialized) {
    return decoder.decodeList(serialized);
  }

  @Override protected boolean isSampled(Span span) {
    return sampler.isSampled(span.traceId(), span.debug());
  }

  @Override protected void record(List<Span> sampled, Callback<Void> callback) {
    storage.v2AsyncSpanConsumer().accept(sampled).enqueue(new V2CallbackAdapter(callback));
  }

  @Override protected String idString(Span span) {
    return span.idString();
  }
}
