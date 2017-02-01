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
package zipkin.autoconfigure.storage.elasticsearch;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.internal.Nullable;
import zipkin.storage.elasticsearch.ElasticsearchStorage;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinElasticsearchStorageProperties {
  /** The elasticsearch cluster to connect to, defaults to "elasticsearch". */
  private String cluster = "elasticsearch";
  /** A List of transport-specific hosts to connect to, e.g. "localhost:9300" */
  private List<String> hosts; // initialize to null to defer default to transport
  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** The date separator used to create the index name. Default to -. */
  private char dateSeparator = '-';
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;

  public String getCluster() {
    return cluster;
  }

  public ZipkinElasticsearchStorageProperties setCluster(String cluster) {
    this.cluster = cluster;
    return this;
  }

  public List<String> getHosts() {
    return hosts;
  }

  public ZipkinElasticsearchStorageProperties setHosts(List<String> hosts) {
    if (hosts != null && !hosts.isEmpty()) {
      this.hosts = hosts;
    }
    return this;
  }

  public String getIndex() {
    return index;
  }

  public ZipkinElasticsearchStorageProperties setIndex(String index) {
    this.index = index;
    return this;
  }

  public int getIndexShards() {
    return indexShards;
  }

  public void setIndexShards(int indexShards) {
    this.indexShards = indexShards;
  }

  public char getDateSeparator() {
    return dateSeparator;
  }

  public void setDateSeparator(char dateSeparator) {
    this.dateSeparator = dateSeparator;
  }

  public int getIndexReplicas() {
    return indexReplicas;
  }

  public void setIndexReplicas(int indexReplicas) {
    this.indexReplicas = indexReplicas;
  }

  ElasticsearchStorage.Builder toBuilder(
      @Nullable InternalElasticsearchClient.Builder clientBuilder) {
    ElasticsearchStorage.Builder result = clientBuilder != null
        ? ElasticsearchStorage.builder(clientBuilder)
        : ElasticsearchStorage.builder();
    if (hosts != null) result.hosts(hosts);
    return result.cluster(cluster)
        .index(index)
        .dateSeparator(dateSeparator)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }
}
