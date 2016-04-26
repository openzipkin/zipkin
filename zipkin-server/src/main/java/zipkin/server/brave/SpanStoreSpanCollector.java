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
package zipkin.server.brave;

import com.github.kristofa.brave.AbstractSpanCollector;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.SpanCodec;
import java.io.IOException;
import java.util.List;
import zipkin.AsyncSpanConsumer;
import zipkin.Codec;
import zipkin.Sampler;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.StorageComponent;

/**
 * A Brave {@link SpanCollector} that forwards to the local {@link SpanStore}.
 */
public class SpanStoreSpanCollector extends AbstractSpanCollector {
  private final StorageComponent storage;

  public SpanStoreSpanCollector(StorageComponent storage, int flushInterval) {
    super(SpanCodec.JSON, new EmptySpanCollectorMetricsHandler(), checkPositive(flushInterval));
    this.storage = storage;
  }

  private static int checkPositive(int flushInterval) {
    if (flushInterval <= 0) {
      throw new IllegalArgumentException("flushInterval must be a positive duration in seconds");
    }
    return flushInterval;
  }

  @Override
  protected void sendSpans(byte[] json) throws IOException {
    List<Span> spans = Codec.JSON.readSpans(json);
    storage.asyncSpanConsumer(Sampler.ALWAYS_SAMPLE).accept(spans, AsyncSpanConsumer.NOOP_CALLBACK);
  }
}
