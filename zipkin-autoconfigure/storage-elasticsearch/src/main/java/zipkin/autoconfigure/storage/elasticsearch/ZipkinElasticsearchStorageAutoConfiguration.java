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
package zipkin.autoconfigure.storage.elasticsearch;

import java.net.URI;
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

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchStorageAutoConfiguration.HostsArentUrls.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageAutoConfiguration {
  @Bean StorageComponent storage(ZipkinElasticsearchStorageProperties elasticsearch) {
    return elasticsearch.toBuilder().build();
  }

  static final class HostsArentUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      if (hosts == null) return true; // default host is not a url
      return !hostsAreUrls(hosts);
    }
  }

  public static boolean hostsAreUrls(String hostsProperty) {
    if (!hostsProperty.startsWith("http")) return false;
    for (String host : hostsProperty.split(",")) {
      String maybeScheme;
      try {
        maybeScheme = URI.create(host).getScheme();
      } catch (IllegalArgumentException ex) {
        return false;
      }
      if (!"http".equals(maybeScheme) && !"https".equals(maybeScheme)) {
        return false;
      }
    }
    return true;
  }
}
