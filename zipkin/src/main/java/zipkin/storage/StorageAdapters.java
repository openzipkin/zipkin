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
package zipkin.storage;

import java.util.List;
import java.util.concurrent.Executor;
import zipkin.Span;

/** Adapters that convert storage components. */
public final class StorageAdapters {

  // Exposed as this project isn't Java 8. Otherwise, it would be Consumer<List<Span>>
  public interface SpanConsumer {
    /** Like {@link AsyncSpanConsumer#accept}, except blocking. Invoked by an executor. */
    void accept(List<Span> spans);
  }

  public static AsyncSpanStore blockingToAsync(SpanStore delegate, Executor executor) {
    if (delegate instanceof InternalAsyncToBlockingSpanStoreAdapter) {
      return ((InternalAsyncToBlockingSpanStoreAdapter) delegate).delegate;
    }
    return new InternalBlockingToAsyncSpanStoreAdapter(delegate, executor);
  }

  public static AsyncSpanConsumer blockingToAsync(SpanConsumer delegate, Executor executor) {
    return new InternalBlockingToAsyncSpanConsumerAdapter(delegate, executor);
  }

  public static SpanStore asyncToBlocking(AsyncSpanStore delegate) {
    if (delegate instanceof InternalBlockingToAsyncSpanStoreAdapter) {
      return ((InternalBlockingToAsyncSpanStoreAdapter) delegate).delegate;
    }
    return new InternalAsyncToBlockingSpanStoreAdapter(delegate);
  }

  private StorageAdapters() {
  }
}
