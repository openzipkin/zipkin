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

import java.io.IOException;
import zipkin.internal.v2.storage.InMemoryStorage;

public final class V2InMemoryStorage extends V2StorageComponent {

  public static Builder newBuilder() {
    return new V2InMemoryStorage.Builder();
  }

  public static final class Builder implements zipkin.storage.StorageComponent.Builder {
    final InMemoryStorage.Builder delegate = InMemoryStorage.newBuilder();

    @Override public Builder strictTraceId(boolean strictTraceId) {
      delegate.strictTraceId(strictTraceId);
      return this;
    }

    /** Eldest traces are removed to ensure spans in memory don't exceed this value */
    public Builder maxSpanCount(int maxSpanCount) {
      delegate.maxSpanCount(maxSpanCount);
      return this;
    }

    @Override public V2InMemoryStorage build() {
      return new V2InMemoryStorage(delegate.build());
    }

    Builder() {
    }
  }

  final InMemoryStorage delegate;

  V2InMemoryStorage(InMemoryStorage delegate) {
    this.delegate = delegate;
  }

  @Override public InMemoryStorage v2SpanStore() {
    return delegate;
  }

  @Override public InMemoryStorage v2SpanConsumer() {
    return delegate;
  }

  @Override public CheckResult check() {
    return CheckResult.OK;
  }

  public void clear() {
    delegate.clear();
  }

  @Override public void close() throws IOException {
  }
}
