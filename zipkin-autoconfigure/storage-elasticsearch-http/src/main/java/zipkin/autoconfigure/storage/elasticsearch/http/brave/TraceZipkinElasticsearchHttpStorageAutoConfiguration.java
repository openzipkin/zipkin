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
package zipkin.autoconfigure.storage.elasticsearch.http.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.okhttp.BraveTracingInterceptor;
import java.io.IOException;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Sets up the Elasticsearch tracing in Brave as an initialization. */
@ConditionalOnBean(Brave.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "zipkin2/elasticsearch")
@Configuration
public class TraceZipkinElasticsearchHttpStorageAutoConfiguration {
  // Lazy to unwind a circular dep: we are tracing the storage used by brave
  @Autowired @Lazy Brave brave;

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @ConditionalOnMissingBean
  OkHttpClient.Builder elasticsearchOkHttpClientBuilder() {
    // have to indirect to unwind a circular dependency
    Interceptor tracingInterceptor = new Interceptor() {
      Interceptor delegate = BraveTracingInterceptor.builder(brave)
          .serverName("zipkin2/elasticsearch").build();

      @Override public Response intercept(Chain chain) throws IOException {
        // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
        if (brave.serverSpanThreadBinder().getCurrentServerSpan() != null &&
            brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan() != null) {
          return delegate.intercept(chain);
        }
        return chain.proceed(chain.request());
      }
    };

    BraveExecutorService tracePropagatingExecutor = BraveExecutorService.wrap(
        new Dispatcher().executorService(),
        brave
    );

    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    builder.addInterceptor(tracingInterceptor);
    builder.addNetworkInterceptor(tracingInterceptor);
    builder.dispatcher(new Dispatcher(tracePropagatingExecutor));
    return builder;
  }
}
