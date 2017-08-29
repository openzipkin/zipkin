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

import javax.annotation.Nullable;
import zipkin.internal.v2.storage.SpanConsumer;
import zipkin.internal.v2.storage.SpanStore;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.StorageAdapters;
import zipkin.storage.StorageComponent;

public abstract class V2StorageComponent implements StorageComponent {
  @Override public zipkin.storage.SpanStore spanStore() {
    if (legacyAsyncSpanStore() != null) {
      return StorageAdapters.asyncToBlocking(asyncSpanStore());
    }
    return new V2SpanStoreAdapter(v2SpanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    V2SpanStoreAdapter v2 = new V2SpanStoreAdapter(v2SpanStore());
    AsyncSpanStore legacy = legacyAsyncSpanStore();
    if (legacy == null) return v2;
    // fan out queries as we don't know if old legacy collectors are in use
    return new LenientDoubleCallbackAsyncSpanStore(v2, legacy);
  }

  @Nullable protected AsyncSpanStore legacyAsyncSpanStore() {
    return null;
  }

  public abstract SpanStore v2SpanStore();

  @Override public final AsyncSpanConsumer asyncSpanConsumer() {
    return new V2SpanConsumerAdapter(v2SpanConsumer());
  }

  public abstract SpanConsumer v2SpanConsumer();
}
