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
package zipkin.autoconfigure.storage.elasticsearch;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.storage.elasticsearch.ElasticsearchStorage;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinElasticsearchStorageProperties {
  /** @see ElasticsearchStorage.Builder#cluster(String) */
  private String cluster = "elasticsearch";
  /** @see ElasticsearchStorage.Builder#hosts(List) */
  private List<String> hosts = Collections.singletonList("localhost:9300");
  /** @see ElasticsearchStorage.Builder#index(String) */
  private String index = "zipkin";
  /** @see ElasticsearchStorage.Builder#indexShards(int) */
  private int indexShards = 5;
  /** @see ElasticsearchStorage.Builder#indexReplicas(int) */
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
    this.hosts = hosts;
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

  public int getIndexReplicas() {
    return indexReplicas;
  }

  public void setIndexReplicas(int indexReplicas) {
    this.indexReplicas = indexReplicas;
  }

  public ElasticsearchStorage.Builder toBuilder() {
    return ElasticsearchStorage.builder()
        .cluster(cluster)
        .hosts(hosts)
        .index(index)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }
}
