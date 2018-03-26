/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.okhttp3.TracingCallFactory;
import brave.okhttp3.TracingInterceptor;
import brave.propagation.CurrentTraceContext;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@Configuration
class TracingZipkinElasticsearchHttpStorageAutoConfiguration {
  // Lazy to unwind a circular dep: we are tracing the storage used by brave
  @Autowired @Lazy HttpTracing httpTracing;

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @ConditionalOnMissingBean
  // our elasticsearch impl uses an instance of OkHttpClient, not Call.Factory, so we have to trace
  // carefully the pieces inside OkHttpClient
  OkHttpClient.Builder elasticsearchOkHttpClientBuilder() {
    ExecutorService tracingExecutor = httpTracing.tracing().currentTraceContext().executorService(
        new Dispatcher().executorService()
    );
    Tracer tracer = httpTracing.tracing().tracer();
    CurrentTraceContext currentTraceContext = httpTracing.tracing().currentTraceContext();
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    builder.addInterceptor(new Interceptor() {
      /** create a local span with the same name as the request tag */
      @Override public Response intercept(Chain chain) throws IOException {
        // don't start new traces (to prevent amplifying writes to local storage)
        if (currentTraceContext.get() == null) return chain.proceed(chain.request());

        Request request = chain.request();
        brave.Span span = tracer.nextSpan().name(request.tag().toString());
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span.start())) {
          return chain.proceed(request);
        } finally {
          span.finish();
        }
      }
    });
    builder.addNetworkInterceptor(TracingInterceptor.create(httpTracing.clientOf("elasticsearch")));
    builder.dispatcher(new Dispatcher(tracingExecutor));
    return builder;
  }
}
