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
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageProperties;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchHttpStorageAutoConfiguration;
import zipkin.storage.elasticsearch.ElasticsearchStorage;
import zipkin.storage.elasticsearch.http.HttpClient;

@ConfigurationProperties("zipkin.storage.elasticsearch")
public class ZipkinElasticsearchAwsStorageProperties {

  static final Logger logger =
      LoggerFactory.getLogger(ZipkinElasticsearchAwsStorageProperties.class);

  /**
   * A List of hosts to connect to, e.g. "https://search-domain-xyzzy.us-west-2.es.amazonaws.com"
   *
   * <p>Exclusive with a named AWS domain.
   */
  private List<String> hosts = Collections.emptyList();
  /** The index prefix to use when generating daily index names. Defaults to zipkin. */
  private String index = "zipkin";
  /** Number of shards (horizontal scaling factor) per index. Defaults to 5. */
  private int indexShards = 5;
  /** Number of replicas (redundancy factor) per index. Defaults to 1.` */
  private int indexReplicas = 1;
  private AwsProperties aws = new AwsProperties();

  public List<String> getHosts() {
    return hosts;
  }

  public ZipkinElasticsearchAwsStorageProperties setHosts(List<String> hosts) {
    this.hosts = hosts;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public ZipkinElasticsearchAwsStorageProperties setIndex(String index) {
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

  static final class AwsProperties {
    /** The name of a domain to look up by endpoint. Exclusive with hosts list. */
    private String domain;

    /**
     * The optional region to search for the domain {@link #domain}. Defaults the usual
     * way (AWS_REGION, DEFAULT_AWS_REGION, etc.).
     */
    private String region;

    public String getDomain() {
      return domain;
    }

    public void setDomain(String domain) {
      this.domain = "".equals(domain) ? null : domain;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = "".equals(region) ? null : region;
    }
  }

  ElasticsearchStorage.Builder toBuilder() {
    if (!hostsIsDefaultOrEmpty() && aws.domain != null) {
      logger.warn("Expected exactly one of hosts or domain: instead saw hosts '{}' and domain '{}'."
          + " Ignoring hosts and proceeding to look for domain. Either unset ES_HOSTS or "
          + "ES_AWS_DOMAIN to suppress this message.", hosts, aws.domain);
    }

    String region;
    if (aws.domain != null) {
      region = aws.region;
      if (region == null) region = new DefaultAwsRegionProviderChain().getRegion();
    } else {
      region = ZipkinElasticsearchHttpStorageAutoConfiguration.regionFromAwsUrls(hosts).get();
    }
    HttpClient.Builder httpBuilder = new HttpClient.Builder()
        .addPostInterceptor(
            new AwsSignatureInterceptor("es", region, new DefaultAWSCredentialsProviderChain())
        );

    if (aws.domain != null) {
      httpBuilder.hosts(new ElasticsearchDomainEndpoint(aws.domain, region));
    } else {
      httpBuilder.hosts(this.hosts);
    }

    return ElasticsearchStorage.builder(httpBuilder)
        .index(index)
        .indexShards(indexShards)
        .indexReplicas(indexReplicas);
  }

  private boolean hostsIsDefaultOrEmpty() {
    return hosts == null || hosts.isEmpty() ||
        hosts.equals(new ZipkinElasticsearchStorageProperties().getHosts());
  }
}
