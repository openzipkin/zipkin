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

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchHttpStorageAutoConfiguration;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchOkHttpAutoConfiguration;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinElasticsearchHttpStorageAutoConfigurationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesntProvideClientBuilder_whenStorageTypeNotElasticsearch() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:cassandra");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchHttpStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(InternalElasticsearchClient.Builder.class);
  }

  @Test
  public void providesClientBuilder_whenStorageTypeElasticsearchAndHostsAreUrls() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.hosts:http://host1:9200"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchOkHttpAutoConfiguration.class,
        ZipkinElasticsearchHttpStorageAutoConfiguration.class);
    context.refresh();

    assertThat(context.getBean(InternalElasticsearchClient.Builder.class)).isNotNull();
  }

  @Test
  public void doesntProvideClientBuilder_whenStorageTypeElasticsearchAndHostsNotUrls() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchHttpStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(InternalElasticsearchClient.Builder.class);
  }

  @Configuration
  static class InterceptorConfiguration {

    static Interceptor one = chain -> null;
    static Interceptor two = chain -> null;

    @Bean @Qualifier("zipkinElasticsearchHttp") Interceptor one() {
      return one;
    }

    @Bean @Qualifier("zipkinElasticsearchHttp") Interceptor two() {
      return two;
    }
  }

  /** Ensures we can wire up network interceptors, such as for logging or authentication */
  @Test
  public void usesInterceptorsQualifiedWith_zipkinElasticsearchHttp() {

    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.hosts:http://host1:9200"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchOkHttpAutoConfiguration.class,
        ZipkinElasticsearchHttpStorageAutoConfiguration.class,
        InterceptorConfiguration.class);
    context.refresh();

    assertThat(context.getBean(OkHttpClient.class).networkInterceptors())
        .containsExactly(InterceptorConfiguration.one, InterceptorConfiguration.two);
  }
}
