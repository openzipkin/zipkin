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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;

/**
 * Settings for Elasticsearch client connection
 * <pre>{@code
 * zipkin.storage.elasticsearch:
 *   hosts: localhost:9200
 *   pipeline: my_pipeline
 *   timeout: 10000
 *   index: zipkin
 *   date-separator: -
 *   index-shards: 5
 *   index-replicas: 1
 *   username: username
 *   password: password
 *   http-logging: HEADERS
 *   ssl:
 *     key-store: keystore.p12
 *     key-store-password: changeme
 *     key-store-type: PKCS12
 *     trust-store: truststore.p12
 *     trust-store-password: changeme
 *     trust-store-type: PKCS12
 *   health-check:
 *     enabled: true
 *     interval: 3s
 * }</pre>
 */
@ConfigurationProperties("zipkin.storage.elasticsearch")
class ZipkinElasticsearchStorageProperties implements Serializable { // for Spark jobs
  /**
   * Sets the level of logging for HTTP requests made by the Elasticsearch client. If not set or
   * none, logging will be disabled.
   */
  enum HttpLogging {
    NONE,
    BASIC,
    HEADERS,
    BODY
  }

  public static class Ssl {
    private String keyStore = emptyToNull(System.getProperty("javax.net.ssl.keyStore"));
    private String keyStorePassword = emptyToNull(System.getProperty("javax.net.ssl.keyStorePassword"));
    private String keyStoreType = emptyToNull(System.getProperty("javax.net.ssl.keyStoreType"));
    private String trustStore = emptyToNull(System.getProperty("javax.net.ssl.trustStore"));
    private String trustStorePassword = emptyToNull(System.getProperty("javax.net.ssl.trustStorePassword"));
    private String trustStoreType = emptyToNull(System.getProperty("javax.net.ssl.trustStoreType"));

    public String getKeyStore() {
      return keyStore;
    }

    public void setKeyStore(String keyStore) {
      this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
      return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreType() {
      return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
    }

    public String getTrustStore() {
      return trustStore;
    }

    public void setTrustStore(String trustStore) {
      this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
      return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreType() {
      return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
    }
  }

  /**
   * Configures the health-checking of endpoints by the Elasticsearch client.
   */
  public static class HealthCheck {
    /** Indicates health checking is enabled. */
    private boolean enabled = true;

    /** The time to wait between sending health check requests. */
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration interval = Duration.ofSeconds(3);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }
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
  private HttpLogging httpLogging = HttpLogging.NONE;
  /** Connect, read and write socket timeouts (in milliseconds) for Elasticsearch Api requests. */
  private Integer timeout = 10_000;
  /** Overrides ssl configuration relating to the Elasticsearch client connection. */
  private Ssl ssl = new Ssl();

  private Integer maxRequests; // unused

  private HealthCheck healthCheck = new HealthCheck();

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

  public HttpLogging getHttpLogging() {
    return httpLogging;
  }

  public void setHttpLogging(HttpLogging httpLogging) {
    this.httpLogging = httpLogging;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  public HealthCheck getHealthCheck() {
    return healthCheck;
  }

  public void setHealthCheck(
    HealthCheck healthCheck) {
    this.healthCheck = healthCheck;
  }

  public Ssl getSsl() {
    return ssl;
  }

  public void setSsl(Ssl ssl) {
    this.ssl = ssl;
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
