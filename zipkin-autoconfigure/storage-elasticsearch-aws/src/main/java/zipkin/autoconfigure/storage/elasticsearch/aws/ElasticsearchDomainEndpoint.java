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

import com.amazonaws.services.elasticsearch.AWSElasticsearch;
import com.amazonaws.services.elasticsearch.AWSElasticsearchClientBuilder;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainRequest;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainResult;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.internal.Lazy;

import static zipkin.internal.Util.checkNotNull;

final class ElasticsearchDomainEndpoint extends Lazy<List<String>> {
  static final Logger log = LoggerFactory.getLogger(ElasticsearchDomainEndpoint.class);
  final String domain;
  final String region;

  ElasticsearchDomainEndpoint(String domain, String region) {
    this.domain = checkNotNull(domain, "domain");
    this.region = checkNotNull(region, "region");
  }

  @Override protected List<String> compute() {
    log.debug("looking up endpoint for region {} and domain {}", region, domain);
    AWSElasticsearch es = AWSElasticsearchClientBuilder.standard().withRegion(region).build();
    DescribeElasticsearchDomainResult result = es.describeElasticsearchDomain(
        new DescribeElasticsearchDomainRequest().withDomainName(domain));
    es.shutdown();

    String endpoint = result.getDomainStatus().getEndpoint();
    if (!endpoint.startsWith("https://")) {
      endpoint = "https://" + endpoint;
    }
    log.debug("using endpoint {}", endpoint);
    return ImmutableList.of(endpoint);
  }
}
