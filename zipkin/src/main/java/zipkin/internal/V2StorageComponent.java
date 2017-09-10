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
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.StorageAdapters;
import zipkin.storage.StorageComponent;

/**
 * This is an internal type used to bridge two versions of the storage component.
 *
 * <p>This type is extensible at the moment as we cannot break api in v1. In Zipkin v2, a storage
 * component, such as Elasticsearch, can break api and change the overall type it implements.
 */
public abstract class V2StorageComponent implements StorageComponent {

  public interface LegacySpanStoreProvider {
    /** Returns null when legacy reads are not supported */
    @Nullable AsyncSpanStore legacyAsyncSpanStore();
  }

  public static V2StorageComponent create(zipkin.internal.v2.storage.StorageComponent delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    LegacySpanStoreProvider legacyProvider;
    if (delegate instanceof LegacySpanStoreProvider) {
      legacyProvider = (LegacySpanStoreProvider) delegate;
    } else {
      legacyProvider = null;
    }
    return new V2StorageComponent() {
      @Override protected LegacySpanStoreProvider legacyProvider() {
        return legacyProvider;
      }

      @Override public zipkin.internal.v2.storage.StorageComponent internalDelegate() {
        return delegate;
      }
    };
  }

  protected abstract LegacySpanStoreProvider legacyProvider();

  /**
   * This is a public method, but should not be used outside Zipkin internal code. If you need to
   * use this method, please use shade or another way to protect from api change, as it is declared
   * on an internal type.
   */
  public abstract zipkin.internal.v2.storage.StorageComponent internalDelegate();

  @Override public zipkin.storage.SpanStore spanStore() {
    AsyncSpanStore legacy =
      legacyProvider() != null ? legacyProvider().legacyAsyncSpanStore() : null;
    if (legacy != null) {
      return StorageAdapters.asyncToBlocking(asyncSpanStore());
    }
    return new V2SpanStoreAdapter(internalDelegate().spanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    V2SpanStoreAdapter v2 = new V2SpanStoreAdapter(internalDelegate().spanStore());
    AsyncSpanStore legacy =
      legacyProvider() != null ? legacyProvider().legacyAsyncSpanStore() : null;
    if (legacy == null) return v2;
    // fan out queries as we don't know if old legacy collectors are in use
    return new LenientDoubleCallbackAsyncSpanStore(v2, legacy);
  }

  @Override public final AsyncSpanConsumer asyncSpanConsumer() {
    return new V2SpanConsumerAdapter(internalDelegate().spanConsumer());
  }

  @Override public CheckResult check() {
    zipkin.internal.v2.CheckResult result = internalDelegate().check();
    return result.ok() ? CheckResult.OK : CheckResult.failed(
      result.error() instanceof Exception
        ? ((Exception) result.error())
        : new ExecutionException(result.error())
    );
  }

  @Override public void close() throws IOException {
    internalDelegate().close();
  }

  @Override public String toString() {
    return internalDelegate().toString();
  }
}
