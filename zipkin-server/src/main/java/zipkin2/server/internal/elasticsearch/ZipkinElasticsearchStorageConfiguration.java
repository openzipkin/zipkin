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
package zipkin2.server.internal.elasticsearch;

import brave.CurrentSpanCustomizer;
import brave.SpanCustomizer;
import brave.http.HttpTracing;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.brave.BraveClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.SessionProtocol;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;

import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.Ssl;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageConfiguration {
  static final String QUALIFIER = "zipkinElasticsearch";

  // Exposed as a bean so that zipkin-aws can override this as sourced from the AWS endpoints api
  @Bean @Qualifier(QUALIFIER) @ConditionalOnMissingBean
  Supplier<EndpointGroup> esInitialEndpoints(
    SessionProtocol esSessionProtocol, ZipkinElasticsearchStorageProperties es) {
    return new InitialEndpointSupplier(esSessionProtocol, es.getHosts());
  }

  // Exposed as a bean so that zipkin-aws can override this to always be SSL
  @Bean @Qualifier(QUALIFIER) @ConditionalOnMissingBean
  SessionProtocol esSessionProtocol(ZipkinElasticsearchStorageProperties es) {
    if (es.getHosts() == null) return SessionProtocol.HTTP;
    if (es.getHosts().contains("https://")) return SessionProtocol.HTTPS;
    return SessionProtocol.HTTP;
  }

  // exposed as a bean so that we can test TLS by swapping it out.
  // TODO: see if we can override the TLS via properties instead as that has less surface area.
  @Bean @Qualifier(QUALIFIER) @ConditionalOnMissingBean ClientFactory esClientFactory(
    ZipkinElasticsearchStorageProperties es,
    MeterRegistry meterRegistry) throws Exception {
    ClientFactoryBuilder builder = new ClientFactoryBuilder();

    // Allow use of a custom KeyStore or TrustStore when connecting to Elasticsearch
    Ssl ssl = es.getSsl();
    if (ssl.getKeyStore() != null || ssl.getTrustStore() != null) configureSsl(builder, ssl);

    // Elasticsearch 7 never returns a response when receiving an HTTP/2 preface instead of the more
    // valid behavior of returning a bad request response, so we can't use the preface.\
    // TODO: find or raise a bug with Elastic
    return builder.useHttp2Preface(false)
      .connectTimeoutMillis(es.getTimeout())
      .meterRegistry(meterRegistry)
      .build();
  }

  @Bean HttpClientFactory esHttpClientFactory(ZipkinElasticsearchStorageProperties es,
    @Qualifier(QUALIFIER) ClientFactory factory,
    @Qualifier(QUALIFIER) SessionProtocol protocol,
    @Qualifier(QUALIFIER) List<Consumer<ClientOptionsBuilder>> options
  ) {
    return new HttpClientFactory(es, factory, protocol, options);
  }

  @Bean @ConditionalOnMissingBean StorageComponent storage(
    ZipkinElasticsearchStorageProperties es,
    HttpClientFactory esHttpClientFactory,
    MeterRegistry meterRegistry,
    @Qualifier(QUALIFIER) SessionProtocol protocol,
    @Qualifier(QUALIFIER) Supplier<EndpointGroup> initialEndpoints,
    @Value("${zipkin.query.lookback:86400000}") int namesLookback,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys,
    @Value("${zipkin.storage.autocomplete-ttl:3600000}") int autocompleteTtl,
    @Value("${zipkin.storage.autocomplete-cardinality:20000}") int autocompleteCardinality) {
    ElasticsearchStorage.Builder builder = es
      .toBuilder(new LazyHttpClientImpl(esHttpClientFactory, protocol, initialEndpoints, es,
        meterRegistry))
      .namesLookback(namesLookback)
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .autocompleteTtl(autocompleteTtl)
      .autocompleteCardinality(autocompleteCardinality);

    return builder.build();
  }

  @Bean @Qualifier(QUALIFIER) @Conditional(BasicAuthRequired.class)
  Consumer<ClientOptionsBuilder> esBasicAuth(ZipkinElasticsearchStorageProperties es) {
    return new Consumer<ClientOptionsBuilder>() {
      @Override public void accept(ClientOptionsBuilder client) {
        client.decorator(
          delegate -> new BasicAuthInterceptor(delegate, es.getUsername(), es.getPassword()));
      }

      @Override public String toString() {
        return "BasicAuthCustomizer{basicCredentials=<redacted>}";
      }
    };
  }

  @Bean @Qualifier(QUALIFIER) @ConditionalOnSelfTracing
  Consumer<ClientOptionsBuilder> esTracing(Optional<HttpTracing> maybeHttpTracing) {
    if (!maybeHttpTracing.isPresent()) {
      // TODO: is there a special cased empty consumer we can use here? I suspect debug is cluttered
      // Alternatively, check why we would ever get here if ConditionalOnSelfTracing matches
      return client -> {
      };
    }

    HttpTracing httpTracing = maybeHttpTracing.get().clientOf("elasticsearch");
    SpanCustomizer spanCustomizer = CurrentSpanCustomizer.create(httpTracing.tracing());

    return client -> {
      client.decorator((delegate, ctx, req) -> {
        String name = ctx.attr(HttpCall.NAME).get();
        if (name != null) { // override the span name if set
          spanCustomizer.name(name);
        }
        return delegate.execute(ctx, req);
      });
      // the tracing decorator is added last so that it encloses the attempt to overwrite the name.
      client.decorator(BraveClient.newDecorator(httpTracing));
    };
  }

  static final class BasicAuthRequired implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      String userName =
        condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.username");
      String password =
        condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.password");
      return !isEmpty(userName) && !isEmpty(password);
    }
  }

  static ClientFactoryBuilder configureSsl(ClientFactoryBuilder builder, Ssl ssl) throws Exception {
    final KeyManagerFactory keyManagerFactory = SslUtil.getKeyManagerFactory(ssl);
    final TrustManagerFactory trustManagerFactory = SslUtil.getTrustManagerFactory(ssl);

    return builder.sslContextCustomizer(sslContextBuilder -> {
      sslContextBuilder.keyManager(keyManagerFactory);
      sslContextBuilder.trustManager(trustManagerFactory);
    });
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
