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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import zipkin.internal.Nullable;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.AsyncSpanStore;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

public final class ElasticsearchHttpStorage extends StorageComponent
  implements V2StorageComponent.LegacySpanStoreProvider {

  /** @see ElasticsearchStorage.HostsSupplier */
  public interface HostsSupplier extends ElasticsearchStorage.HostsSupplier {
  }

  public static Builder builder(OkHttpClient client) {
    return new Builder(ElasticsearchStorage.newBuilder(client)).legacyReadsEnabled(true);
  }

  public static Builder builder() {
    return new Builder(ElasticsearchStorage.newBuilder()).legacyReadsEnabled(true);
  }

  public final Builder toBuilder() {
    return new Builder(ElasticsearchStorage.newBuilder()).legacyReadsEnabled(true);
  }

  public static final class Builder extends StorageComponent.Builder {
    final ElasticsearchStorage.Builder delegate;
    boolean legacyReadsEnabled, searchEnabled;

    Builder(ElasticsearchStorage.Builder delegate) {
      this.delegate = delegate;
    }

    /** @see ElasticsearchStorage.Builder#hosts(List) */
    public final Builder hosts(final List<String> hosts) {
      delegate.hosts(hosts);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#hostsSupplier(ElasticsearchStorage.HostsSupplier) */
    public final Builder hostsSupplier(ElasticsearchStorage.HostsSupplier hosts) {
      delegate.hostsSupplier(hosts);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#maxRequests(int) */
    public final Builder maxRequests(int maxRequests) {
      delegate.maxRequests(maxRequests);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#pipeline(String) */
    public final Builder pipeline(String pipeline) {
      delegate.pipeline(pipeline);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#namesLookback(int) */
    public final Builder namesLookback(int namesLookback) {
      delegate.namesLookback(namesLookback);
      return this;
    }

    /** When true, Redundantly queries indexes made with pre v1.31 collectors. Defaults to true. */
    public final Builder legacyReadsEnabled(boolean legacyReadsEnabled) {
      this.legacyReadsEnabled = legacyReadsEnabled;
      return this;
    }

    /** Visible for testing */
    public final Builder flushOnWrites(boolean flushOnWrites) {
      delegate.flushOnWrites(flushOnWrites);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#index(String) */
    public final Builder index(String index) {
      delegate.index(index);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#dateSeparator(char) */
    public final Builder dateSeparator(char dateSeparator) {
      delegate.dateSeparator(dateSeparator);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#indexShards(int) */
    public final Builder indexShards(int indexShards) {
      delegate.indexShards(indexShards);
      return this;
    }

    /** @see ElasticsearchStorage.Builder#indexReplicas(int) */
    public final Builder indexReplicas(int indexReplicas) {
      delegate.indexReplicas(indexReplicas);
      return this;
    }

    @Override public final Builder strictTraceId(boolean strictTraceId) {
      delegate.strictTraceId(strictTraceId);
      return this;
    }

    @Override public final Builder searchEnabled(boolean searchEnabled) {
      delegate.searchEnabled(this.searchEnabled = searchEnabled);
      return this;
    }

    @Override public final ElasticsearchHttpStorage build() {
      return new ElasticsearchHttpStorage(delegate.build(), legacyReadsEnabled, searchEnabled);
    }
  }

  public final ElasticsearchStorage delegate;
  final boolean legacyReadsEnabled, searchEnabled;

  ElasticsearchHttpStorage(ElasticsearchStorage delegate, boolean legacyReadsEnabled,
    boolean searchEnabled) {
    this.delegate = delegate;
    this.legacyReadsEnabled = legacyReadsEnabled;
    this.searchEnabled = searchEnabled;
  }

  @Override public SpanStore spanStore() {
    return delegate.spanStore();
  }

  @Override public SpanConsumer spanConsumer() {
    return delegate.spanConsumer();
  }

  @Override @Nullable public AsyncSpanStore legacyAsyncSpanStore() {
    if (!legacyReadsEnabled) return null;
    if (delegate.version() >= 6 /* multi-type (legacy) index isn't possible */) {
      return null;
    }
    return new LegacyElasticsearchHttpSpanStore(delegate);
  }

  @Override public CheckResult check() {
    return delegate.check();
  }

  /** This is a blocking call, only used in tests. */
  void clear() throws IOException {
    delegate.clear();
  }

  @Override public void close() {
    delegate.close();
  }
}
