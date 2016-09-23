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
package zipkin.autoconfigure.storage.elasticsearch.http;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.storage.elasticsearch.ElasticsearchStorage;
import zipkin.storage.elasticsearch.http.HttpClient;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinHttpElasticsearchStorageProperties {
  /**
   * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port
   * format. Defaults to "http://localhost:9200".
   */
  private List<String> hosts = Collections.singletonList("http://localhost:9200");
  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;

  public List<String> getHosts() {
    return hosts;
  }

  public ZipkinHttpElasticsearchStorageProperties setHosts(List<String> hosts) {
    this.hosts = hosts;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public ZipkinHttpElasticsearchStorageProperties setIndex(String index) {
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
    return ElasticsearchStorage.builder(new HttpClient.Builder())
        .hosts(hosts)
        .index(index)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }
}
