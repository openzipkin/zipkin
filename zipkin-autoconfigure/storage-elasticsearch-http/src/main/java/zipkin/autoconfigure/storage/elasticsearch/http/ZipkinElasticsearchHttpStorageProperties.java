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
package zipkin.autoconfigure.storage.elasticsearch.http;

import java.util.List;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.storage.elasticsearch.http.ElasticsearchHttpStorage;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinElasticsearchHttpStorageProperties {
  /** Indicates the ingest pipeline used before spans are indexed. no default */
  private String pipeline;
  /** A List of transport-specific hosts to connect to, e.g. "localhost:9300" */
  private List<String> hosts; // initialize to null to defer default to transport
  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** The date separator used to create the index name. Default to -. */
  private char dateSeparator = '-';
  /** Sets maximum in-flight requests from this process to any Elasticsearch host. Defaults to 64 */
  private int maxRequests = 64;
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;

  public String getPipeline() {
    return pipeline;
  }

  public void setPipeline(String pipeline) {
    if (pipeline != null && !pipeline.isEmpty()) {
      this.pipeline = pipeline;
    }
  }

  public List<String> getHosts() {
    return hosts;
  }

  public void setHosts(List<String> hosts) {
    if (hosts != null && !hosts.isEmpty()) {
      this.hosts = hosts;
    }
  }

  public String getIndex() {
    return index;
  }

  public int getMaxRequests() {
    return maxRequests;
  }

  public void setMaxRequests(int maxRequests) {
    this.maxRequests = maxRequests;
  }

  public void setIndex(String index) {
    this.index = index;
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

  public ElasticsearchHttpStorage.Builder toBuilder(OkHttpClient client) {
    ElasticsearchHttpStorage.Builder builder = ElasticsearchHttpStorage.builder(client);
    if (hosts != null) builder.hosts(hosts);
    return builder
        .index(index)
        .dateSeparator(dateSeparator)
        .pipeline(pipeline)
        .maxRequests(maxRequests)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }
}
