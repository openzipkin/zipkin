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
import java.io.IOException;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

/**
 * Storage component that traces each method invocation with Zipkin.
 *
 * <p>Note: this inherits the {@link StorageComponent.Builder#strictTraceId(boolean)} from the
 * delegate.
 */
public final class TracedStorageComponent implements StorageComponent {
  private final Brave brave;
  private final StorageComponent delegate;

  public TracedStorageComponent(Brave brave, StorageComponent delegate) {
    this.brave = brave;
    this.delegate = delegate;
  }

  @Override public SpanStore spanStore() {
    return new TracedSpanStore(brave.localTracer(), delegate);
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return delegate.asyncSpanStore();
  }

  @Override
  public AsyncSpanConsumer asyncSpanConsumer() {
    return new TracedAsyncSpanConsumer(brave, delegate.asyncSpanConsumer());
  }

  @Override public CheckResult check() {
    return delegate.check();
  }

  @Override public void close() throws IOException {
    delegate.close();
  }
}
