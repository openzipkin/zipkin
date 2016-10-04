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

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;
import zipkin.storage.elasticsearch.http.HttpClientBuilder;

@Configuration
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Conditional(ZipkinElasticsearchHttpStorageAutoConfiguration.HostsAreUrls.class)
public class ZipkinElasticsearchHttpStorageAutoConfiguration {

  @Autowired(required = false)
  @Qualifier("zipkinElasticsearchHttpAuthentication")
  Interceptor zipkinElasticsearchHttpAuthentication;

  @Autowired(required = false)
  @Qualifier("zipkinElasticsearchHttp")
  OkHttpClient.Builder elasticsearchOkHttpClientBuilder;

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @ConditionalOnMissingBean
  OkHttpClient elasticsearchOkHttpClient() {
    OkHttpClient.Builder builder = elasticsearchOkHttpClientBuilder != null
        ? elasticsearchOkHttpClientBuilder
        : new OkHttpClient.Builder();

    if (zipkinElasticsearchHttpAuthentication != null) {
      builder.addNetworkInterceptor(zipkinElasticsearchHttpAuthentication);
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean
  InternalElasticsearchClient.Builder clientBuilder(
      @Qualifier("zipkinElasticsearchHttp") OkHttpClient client) {
    return HttpClientBuilder.create(client);
  }

  /** cheap check to see if we are likely to include urls */
  static final class HostsAreUrls implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata md) {
      String hosts = condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.hosts");
      if (hosts == null) return false;
      return hosts.contains("http://") || hosts.contains("https://");
    }
  }
}
