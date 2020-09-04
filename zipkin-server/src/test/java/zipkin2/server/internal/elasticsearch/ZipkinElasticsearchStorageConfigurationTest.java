/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.SessionProtocol;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.elasticsearch.ITElasticsearchDynamicCredentials.pathOfResource;

public class ZipkinElasticsearchStorageConfigurationTest {
  final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void doesntProvideStorageComponent_whenStorageTypeNotElasticsearch() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    es();
  }

  @Test public void providesStorageComponent_whenStorageTypeElasticsearchAndHostsAreUrls() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).isNotNull();
  }

  @Test public void canOverridesProperty_hostsWithList() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200,http://host2:9200")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(context.getBean(ZipkinElasticsearchStorageProperties.class).getHosts())
      .isEqualTo("http://host1:9200,http://host2:9200");
  }

  @Test public void decentToString_whenUnresolvedOrUnhealthy() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://127.0.0.1:9200,http://127.0.0.1:9201")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).hasToString(
      "ElasticsearchStorage{initialEndpoints=http://127.0.0.1:9200,http://127.0.0.1:9201, index=zipkin}");
  }

  @Test public void configuresPipeline() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.pipeline:zipkin")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().pipeline()).isEqualTo("zipkin");
  }

  @Test public void httpPrefixOptional() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9200")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(context.getBean(SessionProtocol.class))
      .isEqualTo(SessionProtocol.HTTP);
  }

  @Test public void https() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:https://localhost")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(context.getBean(SessionProtocol.class))
      .isEqualTo(SessionProtocol.HTTPS);
    assertThat(context.getBean(InitialEndpointSupplier.class).get().endpoints().get(0).port())
      .isEqualTo(443);
  }

  @Configuration
  static class CustomizerConfiguration {

    @Bean @Qualifier("zipkinElasticsearch") public Consumer<ClientOptionsBuilder> one() {
      return one;
    }

    @Bean @Qualifier("zipkinElasticsearch") public Consumer<ClientOptionsBuilder> two() {
      return two;
    }

    Consumer<ClientOptionsBuilder> one = client -> client.maxResponseLength(12345L);
    Consumer<ClientOptionsBuilder> two =
      client -> client.addHeader("test", "bar");
  }

  /** Ensures we can wire up network interceptors, such as for logging or authentication */
  @Test public void usesInterceptorsQualifiedWith_zipkinElasticsearchHttp() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
    Access.registerElasticsearch(context);
    context.register(CustomizerConfiguration.class);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);
    assertThat(factory.options.maxResponseLength()).isEqualTo(12345L);
    assertThat(factory.options.headers().get("test")).isEqualTo("bar");
  }

  @Test public void timeout_defaultsTo10Seconds() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);
    // TODO(anuraaga): Verify connect timeout after https://github.com/line/armeria/issues/1890
    assertThat(factory.options.responseTimeoutMillis()).isEqualTo(10000L);
    assertThat(factory.options.writeTimeoutMillis()).isEqualTo(10000L);
  }

  @Test public void timeout_override() {
    long timeout = 30000L;
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234",
      "zipkin.storage.elasticsearch.timeout:" + timeout)
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);
    // TODO(anuraaga): Verify connect timeout after https://github.com/line/armeria/issues/1890
    assertThat(factory.options.responseTimeoutMillis()).isEqualTo(timeout);
    assertThat(factory.options.writeTimeoutMillis()).isEqualTo(timeout);
  }

  @Test public void strictTraceId_defaultsToTrue() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
    assertThat(es().strictTraceId()).isTrue();
  }

  @Test public void strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().strictTraceId()).isFalse();
  }

  @Test public void dailyIndexFormat() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-1970-01-01");
  }

  @Test public void dailyIndexFormat_overridingPrefix() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.index:zipkin_prod")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin_prod*span-1970-01-01");
  }

  @Test public void dailyIndexFormat_overridingDateSeparator() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:.")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-1970.01.01");
  }

  @Test public void dailyIndexFormat_overridingDateSeparator_empty() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-19700101");
  }

  @Test(expected = BeanCreationException.class)
  public void dailyIndexFormat_overridingDateSeparator_invalidToBeMultiChar() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:blagho")
      .applyTo(context);
    Access.registerElasticsearch(context);

    context.refresh();
  }

  @Test public void namesLookbackAssignedFromQueryLookback() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.query.lookback:" + TimeUnit.DAYS.toMillis(2))
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es().namesLookback()).isEqualTo((int) TimeUnit.DAYS.toMillis(2));
  }

  @Test
  public void doesntProvideBasicAuthInterceptor_whenBasicAuthUserNameandPasswordNotConfigured() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);
    WebClient client = WebClient.builder("http://127.0.0.1:1234")
      .option(ClientOptions.DECORATION, factory.options.decoration())
      .build();
    assertThat(client.as(BasicAuthInterceptor.class)).isNull();
  }

  @Test public void providesBasicAuthInterceptor_whenBasicAuthUserNameAndPasswordConfigured() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234",
      "zipkin.storage.elasticsearch.username:somename",
      "zipkin.storage.elasticsearch.password:pass")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);

    WebClient client = WebClient.builder("http://127.0.0.1:1234")
      .option(ClientOptions.DECORATION, factory.options.decoration())
      .build();
    assertThat(client.as(BasicAuthInterceptor.class)).isNotNull();
  }

  @Test
  public void providesBasicAuthInterceptor_whenDynamicCredentialsConfigured() {
    String credentialsFile = pathOfResource("es-credentials");
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234",
      "zipkin.storage.elasticsearch.credentials-file:" + credentialsFile,
      "zipkin.storage.elasticsearch.credentials-refresh-interval:2")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    HttpClientFactory factory = context.getBean(HttpClientFactory.class);

    WebClient client = WebClient.builder("http://127.0.0.1:1234")
      .option(ClientOptions.DECORATION, factory.options.decoration())
      .build();
    assertThat(client.as(BasicAuthInterceptor.class)).isNotNull();
    BasicCredentials basicCredentials =
      Objects.requireNonNull(client.as(BasicAuthInterceptor.class)).basicCredentials;
    String credentials = basicCredentials.getCredentials();
    assertThat(credentials).isEqualTo("Basic Zm9vOmJhcg==");
  }

  @Test(expected = BeanCreationException.class)
  public void providesBasicAuthInterceptor_whenInvalidDynamicCredentialsConfigured() {
    String credentialsFile = pathOfResource("es-credentials-invalid");
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234",
      "zipkin.storage.elasticsearch.credentials-file:" + credentialsFile,
      "zipkin.storage.elasticsearch.credentials-refresh-interval:2")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
  }

  @Test(expected = BeanCreationException.class)
  public void providesBasicAuthInterceptor_whenDynamicCredentialsConfiguredButFileAbsent() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234",
      "zipkin.storage.elasticsearch.credentials-file:no-this-file",
      "zipkin.storage.elasticsearch.credentials-refresh-interval:2")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
  }

  @Test public void searchEnabled_false() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("searchEnabled")
      .isEqualTo(false);
  }

  @Test public void autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("autocompleteKeys")
      .isEqualTo(Arrays.asList("environment"));
  }

  @Test public void autocompleteTtl() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-ttl:60000")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("autocompleteTtl")
      .isEqualTo(60000);
  }

  @Test public void autocompleteCardinality() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-cardinality:5000")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("autocompleteCardinality")
      .isEqualTo(5000);
  }

  @Test public void templatePriority_valid() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.template-priority:0")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("templatePriority")
      .isEqualTo(0);
  }

  @Test public void templatePriority_null() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.template-priority:")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    assertThat(es()).extracting("templatePriority")
      .isNull();
  }

  @Test(expected = UnsatisfiedDependencyException.class)
  public void templatePriority_Invalid() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.template-priority:string")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    es();
  }

  ElasticsearchStorage es() {
    return context.getBean(ElasticsearchStorage.class);
  }
}
