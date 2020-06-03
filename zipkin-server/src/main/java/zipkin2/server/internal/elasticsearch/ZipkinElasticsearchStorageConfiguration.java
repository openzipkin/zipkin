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

import brave.CurrentSpanCustomizer;
import brave.SpanCustomizer;
import brave.http.HttpTracing;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.brave.BraveClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;

import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.Ssl;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageConfiguration {
  static final String QUALIFIER = "zipkinElasticsearch";
  static final String USERNAME = "zipkin.storage.elasticsearch.username";
  static final String PASSWORD = "zipkin.storage.elasticsearch.password";
  static final String CREDENTIALS_FILE =
    "zipkin.storage.elasticsearch.credentials-file";
  static final String CREDENTIALS_REFRESH_INTERVAL =
    "zipkin.storage.elasticsearch.credentials-refresh-interval";

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
    ClientFactoryBuilder builder = ClientFactory.builder();

    Ssl ssl = es.getSsl();
    if (ssl.isNoVerify()) builder.tlsNoVerify();
    // Allow use of a custom KeyStore or TrustStore when connecting to Elasticsearch
    if (ssl.getKeyStore() != null || ssl.getTrustStore() != null) configureSsl(builder, ssl);

    // Elasticsearch 7 never returns a response when receiving an HTTP/2 preface instead of the more
    // valid behavior of returning a bad request response, so we can't use the preface.
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
  Consumer<ClientOptionsBuilder> esBasicAuth(
    @Qualifier(QUALIFIER) BasicCredentials basicCredentials) {
    return new Consumer<ClientOptionsBuilder>() {
      @Override public void accept(ClientOptionsBuilder client) {
        client.decorator(
          delegate -> new BasicAuthInterceptor(delegate, basicCredentials));
      }

      @Override public String toString() {
        return "BasicAuthCustomizer{basicCredentials=<redacted>}";
      }
    };
  }

  @Bean @Qualifier(QUALIFIER) @Conditional(BasicAuthRequired.class)
  BasicCredentials basicCredentials(ZipkinElasticsearchStorageProperties es) {
    if (isEmpty(es.getUsername()) || isEmpty(es.getPassword())) {
      return new BasicCredentials();
    }
    return new BasicCredentials(es.getUsername(), es.getPassword());
  }

  @Bean(destroyMethod = "shutdown") @Qualifier(QUALIFIER) @Conditional(DynamicRefreshRequired.class)
  ScheduledExecutorService dynamicCredentialsScheduledExecutorService(
    @Value("${" + CREDENTIALS_FILE + "}") String credentialsFile,
    @Value("${" + CREDENTIALS_REFRESH_INTERVAL + "}") Integer credentialsRefreshInterval,
    @Qualifier(QUALIFIER) BasicCredentials basicCredentials) throws IOException {
    ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
      new NamedThreadFactory("zipkin-load-es-credentials"));
    DynamicCredentialsFileLoader credentialsFileLoader =
      new DynamicCredentialsFileLoader(basicCredentials, credentialsFile);
    credentialsFileLoader.updateCredentialsFromProperties();
    ScheduledFuture<?> future = ses.scheduleAtFixedRate(credentialsFileLoader,
        0, credentialsRefreshInterval, TimeUnit.SECONDS);
    if (future.isDone()) throw new RuntimeException("credential refresh thread didn't start");
    return ses;
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
        // We only need the name if it's available and can unsafely access the partially filled log.
        RequestLog log = ctx.log().partial();
        if (log.isAvailable(RequestLogProperty.NAME)) {
          String name = log.name();
          if (name != null) {
            // override the span name if set
            spanCustomizer.name(name);
          }
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
        condition.getEnvironment().getProperty(USERNAME);
      String password =
        condition.getEnvironment().getProperty(PASSWORD);
      String credentialsFile =
        condition.getEnvironment().getProperty(CREDENTIALS_FILE);
      return (!isEmpty(userName) && !isEmpty(password)) || !isEmpty(credentialsFile);
    }
  }

  static final class DynamicRefreshRequired implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      return !isEmpty(condition.getEnvironment().getProperty(CREDENTIALS_FILE));
    }
  }

  static ClientFactoryBuilder configureSsl(ClientFactoryBuilder builder, Ssl ssl) throws Exception {
    final KeyManagerFactory keyManagerFactory = SslUtil.getKeyManagerFactory(ssl);
    final TrustManagerFactory trustManagerFactory = SslUtil.getTrustManagerFactory(ssl);
    return builder.tlsCustomizer(sslContextBuilder -> {
      sslContextBuilder.keyManager(keyManagerFactory);
      sslContextBuilder.trustManager(trustManagerFactory);
    });
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
