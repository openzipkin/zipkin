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
package zipkin.storage.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import zipkin.DependencyLink;
import zipkin.internal.Util;
import zipkin.storage.guava.LazyGuavaStorageComponent;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static zipkin.internal.Util.checkNotNull;

public final class ElasticsearchStorage
    extends LazyGuavaStorageComponent<ElasticsearchSpanStore, ElasticsearchSpanConsumer> {

  /**
   * Internal flag that allows you read-your-writes consistency during tests. With Elasticsearch, it
   * is not sufficient to block on futures since the index also needs to be flushed.
   */
  @VisibleForTesting
  static boolean FLUSH_ON_WRITES;

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    String cluster = "elasticsearch";
    List<String> hosts = Collections.singletonList("localhost:9300");
    String index = "zipkin";

    /**
     * The elasticsearch cluster to connect to, defaults to "elasticsearch".
     */
    public Builder cluster(String cluster) {
      this.cluster = checkNotNull(cluster, "cluster");
      return this;
    }

    /**
     * A comma separated list of elasticsearch hostnodes to connect to, in host:port format. The
     * port should be the transport port, not the http port. Defaults to "localhost:9300".
     */
    public Builder hosts(List<String> hosts) {
      this.hosts = checkNotNull(hosts, "hosts");
      return this;
    }

    /**
     * The index prefix to use when generating daily index names. Defaults to zipkin.
     */
    public Builder index(String index) {
      this.index = checkNotNull(index, "index");
      return this;
    }

    public ElasticsearchStorage build() {
      return new ElasticsearchStorage(this);
    }

    Builder() {
    }
  }

  private final LazyClient lazyClient;
  private final IndexNameFormatter indexNameFormatter;

  ElasticsearchStorage(Builder builder) {
    lazyClient = new LazyClient(builder);
    indexNameFormatter = new IndexNameFormatter(builder.index);
  }

  /** Lazy initializes or returns the client in use by this storage component. */
  public Client client() {
    return lazyClient.get();
  }

  @Override protected ElasticsearchSpanStore computeGuavaSpanStore() {
    return new ElasticsearchSpanStore(lazyClient.get(), indexNameFormatter);
  }

  @Override protected ElasticsearchSpanConsumer computeGuavaSpanConsumer() {
    return new ElasticsearchSpanConsumer(lazyClient.get(), indexNameFormatter);
  }

  @VisibleForTesting void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
    long midnight = Util.midnightUTC(timestampMillis);
    BulkRequestBuilder request = lazyClient.get().prepareBulk();
    for (DependencyLink link : links) {
      request.add(lazyClient.get().prepareIndex(
          indexNameFormatter.indexNameForTimestamp(midnight),
          ElasticsearchConstants.DEPENDENCY_LINK)
          .setSource(
              "parent", link.parent,
              "child", link.child,
              "parent_child", link.parent + "|" + link.child,  // For aggregating callCount
              "callCount", link.callCount));
    }
    request.execute().actionGet();
    lazyClient.get().admin().indices().flush(new FlushRequest()).actionGet();
  }

  @VisibleForTesting void clear() {
    lazyClient.get().admin().indices().delete(new DeleteIndexRequest(indexNameFormatter.catchAll()))
        .actionGet();
    lazyClient.get().admin().indices().flush(new FlushRequest()).actionGet();
  }

  @Override public CheckResult check() {
    try {
      ClusterHealthResponse health = getUnchecked(lazyClient.get()
          .admin().cluster().prepareHealth(indexNameFormatter.catchAll()).execute());
      checkState(health.getStatus() != ClusterHealthStatus.RED, "Health status is RED");
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() {
    lazyClient.close();
  }

  @Override public String toString() {
    return lazyClient.toString();
  }
}
