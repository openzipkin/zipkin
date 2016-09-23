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
package zipkin.storage.elasticsearch;

import com.amazonaws.services.elasticsearch.AWSElasticsearch;
import com.amazonaws.services.elasticsearch.AWSElasticsearchClientBuilder;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainRequest;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainResult;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class LazyDomainLookup implements InternalElasticsearchClient.Builder {

  private final String domain;
  private final String region;
  private final InternalElasticsearchClient.Builder delegate;

  public LazyDomainLookup(String domain, String region, InternalElasticsearchClient.Builder delegate) {
    this.domain = domain;
    this.region = region;
    this.delegate = delegate;
  }

  @Override public InternalElasticsearchClient.Builder cluster(String cluster) {
    throw new UnsupportedOperationException();
  }

  @Override public InternalElasticsearchClient.Builder hosts(List<String> hosts) {
    throw new UnsupportedOperationException();
  }

  @Override public InternalElasticsearchClient.Builder flushOnWrites(boolean flushOnWrites) {
    throw new UnsupportedOperationException();
  }

  @Override public InternalElasticsearchClient.Factory buildFactory() {
    return new InternalElasticsearchClient.Factory() {
      @Override public InternalElasticsearchClient create(String allIndices) {
        AWSElasticsearch es = AWSElasticsearchClientBuilder.standard()
            .withRegion(region)
            .build();

        DescribeElasticsearchDomainResult result =
            es.describeElasticsearchDomain(new DescribeElasticsearchDomainRequest()
                .withDomainName(domain));

        String endpoint = result.getDomainStatus().getEndpoint();
        if (!endpoint.startsWith("https://")) {
          endpoint = "https://" + endpoint;
        }
        delegate.hosts(ImmutableList.of(endpoint));
        return delegate.buildFactory().create(allIndices);
      }
    };

  }
}
