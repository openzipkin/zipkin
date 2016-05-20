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
  /**
   * The elasticsearch cluster to connect to, defaults to "elasticsearch".
   */
  private String cluster = "elasticsearch";

  /**
   * A list of elasticsearch hostnodes to connect to, in host:port format. The port should be the
   * transport port, not the http port. Defaults to "localhost:9300".
   */
  private List<String> hosts = Collections.singletonList("localhost:9300");

  /**
   * The index prefix to use when generating daily index names. Defaults to zipkin.
   */
  private String index = "zipkin";

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

  public ElasticsearchStorage.Builder toBuilder() {
    return ElasticsearchStorage.builder()
        .cluster(cluster)
        .hosts(hosts)
        .index(index);
  }
}
