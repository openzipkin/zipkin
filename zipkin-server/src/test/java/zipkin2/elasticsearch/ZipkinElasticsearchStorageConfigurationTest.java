/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;
import zipkin2.server.internal.elasticsearch.Access;
import zipkin2.server.internal.elasticsearch.HostsConverter;
import zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZipkinElasticsearchStorageConfigurationTest {
  final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void doesntProvideStorageComponent_whenStorageTypeNotElasticsearch() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    es();
  }

  @Test public void providesStorageComponent_whenStorageTypeElasticsearchAndHostsAreUrls() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es()).isNotNull();
  }

  @Test public void canOverridesProperty_hostsWithList() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200,http://host2:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(HostsConverter.convert(
      context.getBean(ZipkinElasticsearchStorageProperties.class).getHosts()))
      .containsExactly(URI.create("http://host1:9200"), URI.create("http://host2:9200"));
  }

  @Test public void configuresPipeline() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.pipeline:zipkin")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().pipeline()).isEqualTo("zipkin");
  }

  /** This helps ensure old setups don't break (provided they have http port 9200 open) */
  @Test public void coersesPort9300To9200() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9300")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(HostsConverter.convert(
      context.getBean(ZipkinElasticsearchStorageProperties.class).getHosts()))
      .containsExactly(URI.create("http://host1:9200"));
  }

  @Test public void httpPrefixOptional() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(context.getBean(SessionProtocol.class))
      .isEqualTo(SessionProtocol.HTTP);
  }

  @Test public void https() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:https://host1:9201")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(context.getBean(SessionProtocol.class))
      .isEqualTo(SessionProtocol.HTTPS);
  }

  @Test public void defaultsToPort9200() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(HostsConverter.convert(
      context.getBean(ZipkinElasticsearchStorageProperties.class).getHosts()))
      .containsExactly(URI.create("http://host1:9200"));
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
      client -> client.addHttpHeader("test", "bar");
  }

  /** Ensures we can wire up network interceptors, such as for logging or authentication */
  @Test public void usesInterceptorsQualifiedWith_zipkinElasticsearchHttp() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.register(CustomizerConfiguration.class);
    context.refresh();

    HttpClient client = context.getBean(LazyHttpClient.class).get();
    assertThat(client.options().maxResponseLength()).isEqualTo(12345L);
    assertThat(client.options().httpHeaders().get("test")).isEqualTo("bar");
  }

  @Test public void timeout_defaultsTo10Seconds() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    HttpClient client = context.getBean(LazyHttpClient.class).get();
    // TODO(anuraaga): Verify connect timeout after https://github.com/line/armeria/issues/1890
    assertThat(client.options().responseTimeoutMillis()).isEqualTo(10000L);
    assertThat(client.options().writeTimeoutMillis()).isEqualTo(10000L);
  }

  @Test public void timeout_override() {
    long timeout = 30000L;
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.timeout:" + timeout)
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    HttpClient client = context.getBean(LazyHttpClient.class).get();
    // TODO(anuraaga): Verify connect timeout after https://github.com/line/armeria/issues/1890
    assertThat(client.options().responseTimeoutMillis()).isEqualTo(timeout);
    assertThat(client.options().writeTimeoutMillis()).isEqualTo(timeout);
  }

  @Test public void strictTraceId_defaultsToTrue() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();
    assertThat(es().strictTraceId()).isTrue();
  }

  @Test public void strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().strictTraceId()).isFalse();
  }

  @Test public void dailyIndexFormat() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
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
    Access.registerElasticsearchHttp(context);
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
    Access.registerElasticsearchHttp(context);
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
    Access.registerElasticsearchHttp(context);
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
    Access.registerElasticsearchHttp(context);

    context.refresh();
  }

  @Test public void namesLookbackAssignedFromQueryLookback() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.query.lookback:" + TimeUnit.DAYS.toMillis(2))
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().namesLookback()).isEqualTo((int) TimeUnit.DAYS.toMillis(2));
  }

  @Test
  public void doesntProvideBasicAuthInterceptor_whenBasicAuthUserNameandPasswordNotConfigured()
    throws Exception {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    HttpClient client = context.getBean(LazyHttpClient.class).get();

    Client<HttpRequest, HttpResponse> delegate = mock(Client.class);
    Client<HttpRequest, HttpResponse> decorated =
      client.options().decoration().decorate(HttpRequest.class, HttpResponse.class, delegate);

    // TODO(anuraaga): This can be cleaner after https://github.com/line/armeria/issues/1883
    HttpRequest req = HttpRequest.of(HttpMethod.GET, "/");
    ClientRequestContext ctx = spy(ClientRequestContext.of(req));
    when(delegate.execute(any(), any())).thenReturn(HttpResponse.of(HttpStatus.OK));

    decorated.execute(ctx, req);

    verify(ctx, never()).addAdditionalRequestHeader(eq(HttpHeaderNames.AUTHORIZATION), any());
  }

  @Test public void providesBasicAuthInterceptor_whenBasicAuthUserNameAndPasswordConfigured()
    throws Exception {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.username:somename",
      "zipkin.storage.elasticsearch.password:pass")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    HttpClient client = context.getBean(LazyHttpClient.class).get();

    Client<HttpRequest, HttpResponse> delegate = mock(Client.class);
    Client<HttpRequest, HttpResponse> decorated = client.options().decoration()
      .decorate(HttpRequest.class, HttpResponse.class, delegate);

    // TODO(anuraaga): This can be cleaner after https://github.com/line/armeria/issues/1883
    HttpRequest req = HttpRequest.of(HttpMethod.GET, "/");
    ClientRequestContext ctx = spy(ClientRequestContext.of(req));
    when(delegate.execute(any(), any())).thenReturn(HttpResponse.of(HttpStatus.OK));

    decorated.execute(ctx, req);

    verify(ctx).addAdditionalRequestHeader(eq(HttpHeaderNames.AUTHORIZATION), any());
  }

  @Test public void searchEnabled_false() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().searchEnabled()).isFalse();
  }

  @Test public void autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().autocompleteKeys())
      .containsOnly("environment");
  }

  @Test public void autocompleteTtl() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-ttl:60000")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().autocompleteTtl())
      .isEqualTo(60000);
  }

  @Test public void autocompleteCardinality() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-cardinality:5000")
      .applyTo(context);
    Access.registerElasticsearchHttp(context);
    context.refresh();

    assertThat(es().autocompleteCardinality())
      .isEqualTo(5000);
  }

  ElasticsearchStorage es() {
    return context.getBean(ElasticsearchStorage.class);
  }
}
