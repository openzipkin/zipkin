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
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import zipkin.storage.elasticsearch.InternalElasticsearchClient.ClientFactory;
import zipkin.storage.elasticsearch.InternalElasticsearchClient.HealthStatus;
import zipkin.storage.guava.LazyGuavaStorageComponent;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
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
    ClientFactory clientFactory = NativeClient.builder().build();
    String index = "zipkin";
    int indexShards = 5;
    int indexReplicas = 1;

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

    /**
     * The client supplier to consume for connectivity to elasticsearch. Defaults to using the
     * transport-client based {@link NativeClient}, but can be overriden with a HTTP-speaking client
     * (e.g. for use in cloud environments where the transport protocol is not exposed).
     */
    public Builder client(ClientFactory clientFactory) {
      this.clientFactory = clientFactory;
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
  @VisibleForTesting InternalElasticsearchClient client() {
    return lazyClient.get();
  }

  @Override protected ElasticsearchSpanStore computeGuavaSpanStore() {
    return new ElasticsearchSpanStore(client(), indexNameFormatter);
  }

  @Override protected ElasticsearchSpanConsumer computeGuavaSpanConsumer() {
    return new ElasticsearchSpanConsumer(client(), indexNameFormatter);
  }

  @VisibleForTesting void clear() {
    lazyClient.get().clear(getOnlyElement(Arrays.asList(indexNameFormatter.catchAll())));
  }

  @Override public CheckResult check() {
    try {
      HealthStatus status = client().clusterHealth(indexNameFormatter.catchAll());
      checkState(status != HealthStatus.RED, "Health status is RED");
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
