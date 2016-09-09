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
package zipkin.autoconfigure.storage.elasticsearch.jest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageAutoConfiguration;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageProperties;
import zipkin.storage.StorageComponent;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchJestStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchStorageAutoConfiguration.UrlMatcher.Matches.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageJestAutoConfiguration {
  @Bean StorageComponent storage(ZipkinElasticsearchJestStorageProperties elasticsearch) {
    return elasticsearch.toBuilder().build();
  }
}
