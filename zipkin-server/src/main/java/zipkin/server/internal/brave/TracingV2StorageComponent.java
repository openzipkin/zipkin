/**
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
package zipkin.server.internal.brave;

import brave.Tracing;
import java.io.IOException;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;

// public for use in ZipkinServerConfiguration
// not making spans for async storage to avoid complexity around V2StorageComponent
public final class TracingV2StorageComponent extends V2StorageComponent {
  private final Tracing tracing;
  private final V2StorageComponent delegate;

  public TracingV2StorageComponent(Tracing tracing, V2StorageComponent delegate) {
    this.tracing = tracing;
    this.delegate = delegate;
  }

  @Override protected LegacySpanStoreProvider legacyProvider() {
    throw new UnsupportedOperationException();
  }

  @Override public zipkin2.storage.StorageComponent delegate() {
    return delegate.delegate();
  }

  @Override public SpanStore spanStore() {
    return new TracingSpanStore(tracing, delegate);
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return delegate.asyncSpanStore();
  }

  @Override public CheckResult check() {
    return delegate.check();
  }

  @Override public void close() throws IOException {
    delegate.close();
  }
}
