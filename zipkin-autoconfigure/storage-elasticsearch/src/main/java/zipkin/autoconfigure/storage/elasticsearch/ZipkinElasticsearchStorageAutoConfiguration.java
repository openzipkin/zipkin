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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchStorageAutoConfiguration.UrlMatcher.DoesNotMatch.class)
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageAutoConfiguration {
  @Bean StorageComponent storage(ZipkinElasticsearchStorageProperties elasticsearch) {
    return elasticsearch.toBuilder().build();
  }

  public static final class UrlMatcher {
    public static final class Matches implements Condition {
      @Override public boolean matches(ConditionContext conditionContext,
          AnnotatedTypeMetadata annotatedTypeMetadata) {
        String hosts =
            conditionContext.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
        return hostsAreUrls(firstNonNull(hosts, ""));
      }
    }

    public static final class DoesNotMatch implements Condition {
      @Override public boolean matches(ConditionContext conditionContext,
          AnnotatedTypeMetadata annotatedTypeMetadata) {
        String hosts =
            conditionContext.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
        return !hostsAreUrls(firstNonNull(hosts, ""));
      }
    }

    private static boolean hostsAreUrls(String hostsProperty) {
      List<String> hosts = Arrays.asList(hostsProperty.split(","));
      try {
        URL url = new URL(Iterables.getFirst(hosts, ""));
        checkArgument(ImmutableSet.of("http", "https").contains(url.getProtocol()), "");
        return true;
      } catch (MalformedURLException |IllegalArgumentException ex) {
        return false;
      }
    }
  }

}
