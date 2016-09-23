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
package zipkin.autoconfigure.storage.elasticsearch.http;

import com.google.common.base.Optional;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import zipkin.storage.StorageComponent;

import static com.google.common.base.Preconditions.checkArgument;

@Configuration
@EnableConfigurationProperties(ZipkinHttpElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional({
    ZipkinHttpElasticsearchStorageAutoConfiguration.HostsAreUrls.class,
    ZipkinHttpElasticsearchStorageAutoConfiguration.HostsArentAwsUrls.class
})
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinHttpElasticsearchStorageAutoConfiguration {
  @Bean StorageComponent storage(ZipkinHttpElasticsearchStorageProperties elasticsearch) {
    return elasticsearch.toBuilder().build();
  }

  static final class HostsAreUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      if (hosts == null) return false;
      return ZipkinElasticsearchStorageAutoConfiguration.hostsAreUrls(hosts);
    }
  }

  static final class HostsArentAwsUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      if (hosts == null) return true;
      return !regionFromAwsUrls(Arrays.asList(hosts.split(","))).isPresent();
    }
  }

  /**
   * Only here for consumption by {@link HostsArentAwsUrls}; ideally we could find a way to express
   * a hierarchy of autoconfigurations in spring without each parent needing to turn themselves off
   * for their children, but there seems to be no reliable way to order autoconfigure'd classes
   * (neither @AutoConfigureBefore nor @AutoConfigureAfter seemd to have the desired effect)
   */
  private static final Pattern AWS_URL =
      Pattern.compile("^https://[^.]+\\.([^.]+)\\.es\\.amazonaws\\.com", Pattern.CASE_INSENSITIVE);

  public static Optional<String> regionFromAwsUrls(List<String> hosts) {
    Optional<String> awsRegion = Optional.absent();
    for (String url : hosts) {
        Matcher matcher = AWS_URL.matcher(url);
        if (matcher.find()) {
          String matched = matcher.group(1);
          checkArgument(awsRegion.or(matched).equals(matched),
              "too many regions: saw '%s' and '%s'", awsRegion, matched);
          awsRegion = Optional.of(matcher.group(1));
        } else {
          checkArgument(!awsRegion.isPresent(),
              "mismatched regions; saw '%s' but no awsRegion found in '%s'", awsRegion.orNull(),
              url);
        }
      }
    return awsRegion;
  }
}
