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
package zipkin.autoconfigure.storage.elasticsearch.http;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This auto-configures the {@linkplain OkHttpClient} used for Elasticsearch.
 *
 * <p>Here are the major features:
 * <pre><ul>
 *   <li>The {@linkplain OkHttpClient.Builder} can be pre-configured (ex self-tracing)</li>
 *   <li>{@linkplain Interceptor} beans will be added before returning (ex auth or logging)</li>
 * </ul></pre>
 *
 * <p>This bean will register even if the http transport isn't in use (ex using Elasticsearch's
 * native api). This is a complexity tradeoff as detecting if http is strictly needed is not
 * straight-forward. For example, even though the hosts might contain http urls, in the case
 * of Amazon, the hosts collection can be blank (lookup host by domain name).
 */
@Configuration
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
public class ZipkinElasticsearchOkHttpAutoConfiguration {

  @Autowired(required = false)
  @Qualifier("zipkinElasticsearchHttp")
  List<Interceptor> networkInterceptors = Collections.emptyList();

  @Autowired(required = false)
  @Qualifier("zipkinElasticsearchHttp")
  OkHttpClient.Builder elasticsearchOkHttpClientBuilder;

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @ConditionalOnMissingBean
  OkHttpClient elasticsearchOkHttpClient(
    @Value("${zipkin.storage.elasticsearch.timeout:10000}") int timeout
  ) {
    OkHttpClient.Builder builder = elasticsearchOkHttpClientBuilder != null
        ? elasticsearchOkHttpClientBuilder
        : new OkHttpClient.Builder();

    for (Interceptor interceptor : networkInterceptors) {
      builder.addNetworkInterceptor(interceptor);
    }
    builder.connectTimeout(timeout, TimeUnit.MILLISECONDS);
    builder.readTimeout(timeout, TimeUnit.MILLISECONDS);
    builder.writeTimeout(timeout, TimeUnit.MILLISECONDS);
    return builder.build();
  }
}
