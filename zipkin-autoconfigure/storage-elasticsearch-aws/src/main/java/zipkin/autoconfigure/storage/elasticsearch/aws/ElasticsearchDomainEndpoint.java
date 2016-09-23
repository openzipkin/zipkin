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
import zipkin.internal.Lazy;

final class ElasticsearchDomainEndpoint extends Lazy<List<String>> {
  final String domain;
  final String region;

  ElasticsearchDomainEndpoint(String domain, String region) {
    this.domain = domain;
    this.region = region;
  }

  @Override protected List<String> compute() {
    AWSElasticsearch es = AWSElasticsearchClientBuilder.standard()
        .withRegion(region)
        .build();

    DescribeElasticsearchDomainResult result =
        es.describeElasticsearchDomain(new DescribeElasticsearchDomainRequest()
            .withDomainName(domain));
    es.shutdown();

    String endpoint = result.getDomainStatus().getEndpoint();
    if (!endpoint.startsWith("https://")) {
      endpoint = "https://" + endpoint;
    }
    return ImmutableList.of(endpoint);
  }
}
