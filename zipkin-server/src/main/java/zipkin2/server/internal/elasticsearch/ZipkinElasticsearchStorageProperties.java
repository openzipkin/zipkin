/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.elasticsearch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.elasticsearch.ElasticsearchStorage;

@ConfigurationProperties("zipkin.storage.elasticsearch")
class ZipkinElasticsearchStorageProperties implements Serializable { // for Spark jobs
  static final Logger log = Logger.getLogger(ZipkinElasticsearchStorageProperties.class.getName());

  private static final long serialVersionUID = 0L;

  /** Indicates the ingest pipeline used before spans are indexed. no default */
  private String pipeline;
  /** A List of base urls to connect to. Defaults to http://localhost:9300 */
  private List<String> hosts; // initialize to null to defer default to transport
  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** The date separator used to create the index name. Default to -. */
  private String dateSeparator = "-";
  /** Sets maximum in-flight requests from this process to any Elasticsearch host. Defaults to 64 (overriden by throttle settings) */
  private int maxRequests = 64;
  /** Overrides maximum in-flight requests to match throttling settings if throttling is enabled. */
  private Integer throttleMaxConcurrency;
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;
  /** username used for basic auth. Needed when Shield or X-Pack security is enabled */
  private String username;
  /** password used for basic auth. Needed when Shield or X-Pack security is enabled */
  private String password;
  /**
   * When set, controls the volume of HTTP logging of the Elasticsearch Api. Options are BASIC,
   * HEADERS, BODY
   */
  private HttpLoggingInterceptor.Level httpLogging;
  /**
   * Controls the connect, read and write socket timeouts (in milliseconds) for Elasticsearch Api
   * requests. Defaults to 10000 (10 seconds)
   */
  private int timeout = 10_000;

  ZipkinElasticsearchStorageProperties(
    @Value("${zipkin.storage.throttle.enabled:false}") boolean throttleEnabled,
    @Value("${zipkin.storage.throttle.max-concurrency:200}") int throttleMaxConcurrency) {
    if (throttleEnabled) {
      this.throttleMaxConcurrency = throttleMaxConcurrency;
    }
  }

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
      List<String> converted = new ArrayList<>();
      for (String host : hosts) {
        if (host.startsWith("http://") || host.startsWith("https://")) {
          converted.add(host);
          continue;
        }
        int port = HttpUrl.parse("http://" + host).port();
        if (port == 80) {
          host += ":9200";
        } else if (port == 9300) {
          log.warning(
              "Native transport no longer supported. Changing " + host + " to http port 9200");
          host = host.replace(":9300", ":9200");
        }
        converted.add("http://" + host);
      }
      this.hosts = converted;
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

  public int getIndexReplicas() {
    return indexReplicas;
  }

  public void setIndexReplicas(int indexReplicas) {
    this.indexReplicas = indexReplicas;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public HttpLoggingInterceptor.Level getHttpLogging() {
    return httpLogging;
  }

  public void setHttpLogging(HttpLoggingInterceptor.Level httpLogging) {
    this.httpLogging = httpLogging;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  public ElasticsearchStorage.Builder toBuilder(OkHttpClient client) {
    ElasticsearchStorage.Builder builder = ElasticsearchStorage.newBuilder(client);
    if (hosts != null) builder.hosts(hosts);
    return builder
        .index(index)
        .dateSeparator(dateSeparator.isEmpty() ? 0 : dateSeparator.charAt(0))
        .pipeline(pipeline)
        .maxRequests(throttleMaxConcurrency == null ? maxRequests : throttleMaxConcurrency)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }
}
