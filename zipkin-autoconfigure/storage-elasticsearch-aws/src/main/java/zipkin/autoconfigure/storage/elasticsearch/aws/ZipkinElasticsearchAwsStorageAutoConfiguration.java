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

import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageAutoConfiguration;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchHttpStorageAutoConfiguration;
import zipkin.storage.StorageComponent;

import static zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchHttpStorageAutoConfiguration.regionFromAwsUrls;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchAwsStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@AutoConfigureBefore({
    ZipkinElasticsearchStorageAutoConfiguration.class,
    ZipkinElasticsearchHttpStorageAutoConfiguration.class
})
@Conditional(ZipkinElasticsearchAwsStorageAutoConfiguration.AwsMagic.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchAwsStorageAutoConfiguration {
  @Bean StorageComponent storage(ZipkinElasticsearchAwsStorageProperties elasticsearch) {
    return elasticsearch.toBuilder().build();
  }

  static final class AwsMagic implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      String domain = condition.getEnvironment()
          .getProperty("zipkin.storage.elasticsearch.aws.domain");

      // If neither hosts nor domain, no AWS magic
      if (isEmpty(hosts) && isEmpty(domain)) return false;

      // Either we have a domain, or we check the hosts auto-detection magic
      return !isEmpty(domain) || regionFromAwsUrls(Arrays.asList(hosts.split(","))).isPresent();
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
