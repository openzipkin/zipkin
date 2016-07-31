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
package zipkin.storage.guava;

import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;

/** Adapters that convert storage components to or from other async libraries. */
public final class GuavaStorageAdapters {

  public static AsyncSpanStore guavaToAsync(GuavaSpanStore delegate) {
    if (delegate instanceof InternalGuavaSpanStoreAdapter) {
      return ((InternalGuavaSpanStoreAdapter) delegate).delegate;
    }
    return new InternalGuavaToAsyncSpanStoreAdapter(delegate);
  }

  public static AsyncSpanConsumer guavaToAsync(GuavaSpanConsumer delegate) {
    if (delegate instanceof InternalGuavaSpanConsumerAdapter) {
      return ((InternalGuavaSpanConsumerAdapter) delegate).delegate;
    }
    return new InternalGuavaToAsyncSpanConsumerAdapter(delegate);
  }

  public static GuavaSpanStore asyncToGuava(AsyncSpanStore delegate) {
    if (delegate instanceof InternalGuavaToAsyncSpanStoreAdapter) {
      return ((InternalGuavaToAsyncSpanStoreAdapter) delegate).delegate;
    }
    return new InternalGuavaSpanStoreAdapter(delegate);
  }

  public static GuavaSpanConsumer asyncToGuava(AsyncSpanConsumer delegate) {
    if (delegate instanceof InternalGuavaToAsyncSpanConsumerAdapter) {
      return ((InternalGuavaToAsyncSpanConsumerAdapter) delegate).delegate;
    }
    return new InternalGuavaSpanConsumerAdapter(delegate);
  }

  private GuavaStorageAdapters() {
  }
}
