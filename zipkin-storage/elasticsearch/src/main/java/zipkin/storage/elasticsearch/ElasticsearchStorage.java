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
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import zipkin.storage.guava.LazyGuavaStorageComponent;

import static com.google.common.base.Preconditions.checkState;
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
    int indexShards = 5;
    int indexReplicas = 1;

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

    /**
     * The number of shards to split the index into. Each shard and its replicas are assigned to a
     * machine in the cluster. Increasing the number of shards and machines in the cluster will
     * improve read and write performance. Number of shards cannot be changed for existing indices,
     * but new daily indices will pick up changes to the setting. Defaults to 5.
     *
     * <p>Corresponds to <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_shards</a>
     */
    public Builder indexShards(int indexShards) {
      this.indexShards = indexShards;
      return this;
    }

    /**
     * The number of replica copies of each shard in the index. Each shard and its replicas are
     * assigned to a machine in the cluster. Increasing the number of replicas and machines in the
     * cluster will improve read performance, but not write performance. Number of replicas can be
     * changed for existing indices. Defaults to 1. It is highly discouraged to set this to 0 as it
     * would mean a machine failure results in data loss.
     *
     * <p>Corresponds to <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_replicas</a>
     */
    public Builder indexReplicas(int indexReplicas) {
      this.indexReplicas = indexReplicas;
      return this;
    }

    public ElasticsearchStorage build() {
      return new ElasticsearchStorage(this);
    }

    Builder() {
    }
  }

  private final LazyClient lazyClient;
  @VisibleForTesting
  final IndexNameFormatter indexNameFormatter;

  ElasticsearchStorage(Builder builder) {
    lazyClient = new LazyClient(builder);
    indexNameFormatter = new IndexNameFormatter(builder.index);
  }

  /** Lazy initializes or returns the client in use by this storage component. */
  public Client client() {
    return lazyClient.get();
  }

  @Override protected ElasticsearchSpanStore computeGuavaSpanStore() {
    return new ElasticsearchSpanStore(client(), indexNameFormatter);
  }

  @Override protected ElasticsearchSpanConsumer computeGuavaSpanConsumer() {
    return new ElasticsearchSpanConsumer(client(), indexNameFormatter);
  }

  @VisibleForTesting void clear() {
    client().admin().indices().delete(new DeleteIndexRequest(indexNameFormatter.catchAll()))
        .actionGet();
    client().admin().indices().flush(new FlushRequest()).actionGet();
  }

  @Override public CheckResult check() {
    try {
      ClusterHealthResponse health =
          client().admin().cluster().prepareHealth(indexNameFormatter.catchAll()).get();
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
