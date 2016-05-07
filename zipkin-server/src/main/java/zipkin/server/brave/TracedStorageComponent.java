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

import com.github.kristofa.brave.Brave;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.SpanStore;
import zipkin.StorageComponent;

public final class TracedStorageComponent implements StorageComponent {
  private final Brave brave;
  private final StorageComponent delegate;

  public TracedStorageComponent(Brave brave, StorageComponent delegate) {
    this.brave = brave;
    this.delegate = delegate;
  }

  @Override public SpanStore spanStore() {
    return new TracedSpanStore(brave.localTracer(), delegate.spanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return delegate.asyncSpanStore();
  }

  @Override
  public AsyncSpanConsumer asyncSpanConsumer(CollectorSampler sampler, CollectorMetrics metrics) {
    return new TracedAsyncSpanConsumer(brave, delegate.asyncSpanConsumer(sampler, metrics));
  }

  @Override public void close() {
    delegate.close();
  }
}
