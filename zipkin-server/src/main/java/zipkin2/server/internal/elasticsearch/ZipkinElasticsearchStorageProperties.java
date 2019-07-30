/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal.elasticsearch;

import java.io.Serializable;
import java.util.logging.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;

@ConfigurationProperties("zipkin.storage.elasticsearch")
class ZipkinElasticsearchStorageProperties implements Serializable { // for Spark jobs
  /**
   * Sets the level of logging for HTTP requests made by the Elasticsearch client. If not set or
   * none, logging will be disabled.
   */
  enum HttpLoggingLevel {
    NONE,
    BASIC,
    HEADERS,
    BODY
  }

  static final Logger log = Logger.getLogger(ZipkinElasticsearchStorageProperties.class.getName());

  private static final long serialVersionUID = 0L;

  /** Indicates the ingest pipeline used before spans are indexed. */
  private String pipeline;
  /** A comma separated list of base urls to connect to. */
  private String hosts = "http://localhost:9200";
  /** The index prefix to use when generating daily index names. */
  private String index;
  /** The date separator used to create the index name. */
  private String dateSeparator;
  /** Number of shards (horizontal scaling factor) per index. */
  private Integer indexShards;
  /** Number of replicas (redundancy factor) per index. */
  private Integer indexReplicas;
  /** username used for basic auth. Needed when Shield or X-Pack security is enabled */
  private String username;
  /** password used for basic auth. Needed when Shield or X-Pack security is enabled */
  private String password;
  /** When set, controls the volume of HTTP logging of the Elasticsearch Api. */
  private HttpLoggingLevel httpLogging = HttpLoggingLevel.NONE;
  /** Connect, read and write socket timeouts (in milliseconds) for Elasticsearch Api requests. */
  private Integer timeout = 10_000;

  private Integer maxRequests; // unused

  public String getPipeline() {
    return pipeline;
  }

  public void setPipeline(String pipeline) {
    this.pipeline = emptyToNull(pipeline);
  }

  public String getHosts() {
    return hosts;
  }

  public void setHosts(String hosts) {
    this.hosts = emptyToNull(hosts);
  }

  public String getIndex() {
    return index;
  }

  public Integer getMaxRequests() {
    return maxRequests;
  }

  public void setMaxRequests(Integer maxRequests) {
    this.maxRequests = maxRequests;
  }

  public void setIndex(String index) {
    this.index = emptyToNull(index);
  }

  public Integer getIndexShards() {
    return indexShards;
  }

  public void setIndexShards(Integer indexShards) {
    this.indexShards = indexShards;
  }

  public String getDateSeparator() {
    return dateSeparator;
  }

  public void setDateSeparator(String dateSeparator) {
    String trimmed = dateSeparator.trim();
    if (trimmed.length() > 1) {
      throw new IllegalArgumentException("dateSeparator must be empty or a single character");
    }
    this.dateSeparator = dateSeparator;
  }

  public Integer getIndexReplicas() {
    return indexReplicas;
  }

  public void setIndexReplicas(Integer indexReplicas) {
    this.indexReplicas = indexReplicas;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = emptyToNull(username);
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = emptyToNull(password);
  }

  public HttpLoggingLevel getHttpLogging() {
    return httpLogging;
  }

  public void setHttpLogging(HttpLoggingLevel httpLogging) {
    this.httpLogging = httpLogging;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  public ElasticsearchStorage.Builder toBuilder(LazyHttpClient httpClient) {
    ElasticsearchStorage.Builder builder = ElasticsearchStorage.newBuilder(httpClient);
    if (index != null) builder.index(index);
    if (dateSeparator != null) {
      builder.dateSeparator(dateSeparator.isEmpty() ? 0 : dateSeparator.charAt(0));
    }
    if (pipeline != null) builder.pipeline(pipeline);
    if (indexShards != null) builder.indexShards(indexShards);
    if (indexReplicas != null) builder.indexReplicas(indexReplicas);

    if (maxRequests != null) {
      log.warning("ES_MAX_REQUESTS is no longer honored. Use STORAGE_THROTTLE_ENABLED instead");
    }
    return builder;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
