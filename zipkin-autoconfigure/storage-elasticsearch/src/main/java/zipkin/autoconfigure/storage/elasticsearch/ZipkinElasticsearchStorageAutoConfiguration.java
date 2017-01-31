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
package zipkin.autoconfigure.storage.elasticsearch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.storage.StorageComponent;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchStorageAutoConfiguration.HostsArentUrls.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageAutoConfiguration {
  @Autowired(required = false)
  InternalElasticsearchClient.Builder clientBuilder;

  @Bean StorageComponent storage(ZipkinElasticsearchStorageProperties elasticsearch,
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId) {
    return elasticsearch.toBuilder(clientBuilder).strictTraceId(strictTraceId).build();
  }

  /** switch off when elasticsearch-http is likely in use. */
  static final class HostsArentUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      return hosts == null || (!hosts.contains("http://") && !hosts.contains("https://"));
    }
  }
}
