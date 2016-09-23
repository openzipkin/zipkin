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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageProperties;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinHttpElasticsearchStorageAutoConfiguration;
import zipkin.storage.elasticsearch.ElasticsearchStorage;
import zipkin.storage.elasticsearch.LazyDomainLookup;
import zipkin.storage.elasticsearch.http.HttpClient;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinAwsElasticsearchStorageProperties {

  final Logger logger = LoggerFactory.getLogger(ZipkinAwsElasticsearchStorageProperties.class);

  /**
   * A List of hosts to connect to, e.g. "https://search-domain-xyzzy.us-west-2.es.amazonaws.com"
   *
   * Exclusive with a named AWS domain.
   */
  private List<String> hosts = Collections.emptyList();

  final class AwsProperties {
    /** The name of a domain to look up by endpoint. Exclusive with hosts list. */
    private Optional<String> domain = Optional.absent();

    /**
     * The optional region to search for the domain {@link #domain}. Defaults the usual
     * way (AWS_REGION, DEFAULT_AWS_REGION, etc.).
     */
    private Optional<String> region = Optional.absent();

    public Optional<String> getDomain() {
      return domain;
    }

    public void setDomain(String domain) {
      this.domain =
          domain == null || domain.isEmpty() ? Optional.<String>absent() : Optional.of(domain);
    }

    public Optional<String> getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region =
          region == null || region.isEmpty() ? Optional.<String>absent() : Optional.of(region);
    }
  }

  private AwsProperties aws = new AwsProperties();

  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;

  public List<String> getHosts() {
    return hosts;
  }

  public ZipkinAwsElasticsearchStorageProperties setHosts(List<String> hosts) {
    this.hosts = hosts;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public ZipkinAwsElasticsearchStorageProperties setIndex(String index) {
    this.index = index;
    return this;
  }

  public AwsProperties getAws() {
    return aws;
  }

  public void setAws(AwsProperties aws) {
    this.aws = aws;
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
    if (!hostsIsDefaultOrEmpty() && !domainIsEmpty()) {
      logger.warn("Expected exactly one of hosts or domain: instead saw hosts '{}' and domain '{}'."
          + " Ignoring hosts and proceeding to look for domain. Either unset ES_HOSTS or "
          + "ES_AWS_DOMAIN to suppress this message.", hosts, aws.domain.or(""));
    }

    String region;
    if (aws.domain.isPresent()) {
      region = aws.region.or(LazyRegion.INSTANCE);
    } else {
      region = ZipkinHttpElasticsearchStorageAutoConfiguration.regionFromAwsUrls(this.hosts).get();
    }
    HttpClient.Builder httpBuilder = new HttpClient.Builder()
        .addPostInterceptor(
            new AwsSignatureInterceptor("es", region, new DefaultAWSCredentialsProviderChain())
        );

    ElasticsearchStorage.Builder builder;
    if (aws.domain.isPresent()) {
      builder =
          ElasticsearchStorage.builder(new LazyDomainLookup(aws.domain.get(), region, httpBuilder));
    } else {
      builder = ElasticsearchStorage.builder(httpBuilder.hosts(this.hosts));
    }

    return builder
        .index(index)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }


  private boolean hostsIsDefaultOrEmpty() {
    return hosts == null || hosts.isEmpty() ||
        hosts.equals(new ZipkinElasticsearchStorageProperties().getHosts());
  }

  private boolean domainIsEmpty() {
    return !aws.domain.isPresent() || aws.domain.get().isEmpty();
  }

  private enum LazyRegion implements Supplier<String> {
    INSTANCE;

    final DefaultAwsRegionProviderChain chain = new DefaultAwsRegionProviderChain();

    @Override public String get() {
      // Lazy because this throws if it can't find a region
      return chain.getRegion();
    }
  }
}
