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

import brave.Tracing;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.brave.BraveClient;
import com.linecorp.armeria.client.logging.LoggingClientBuilder;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.logging.LogLevel;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
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
import zipkin2.elasticsearch.ElasticsearchStorage.HostsSupplier;
import zipkin2.elasticsearch.internal.client.RawContentLoggingClient;
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageAutoConfiguration {
  static final String QUALIFIER = "zipkinElasticsearchHttp";

  @Bean @Qualifier(QUALIFIER) Consumer<HttpClientBuilder> zipkinElasticsearchHttp(
    @Value("${zipkin.storage.elasticsearch.timeout:10000}") int timeout) {
    return new Consumer<HttpClientBuilder>() {
      @Override public void accept(HttpClientBuilder client) {
        client.responseTimeoutMillis(timeout).writeTimeoutMillis(timeout);
      }

      @Override public String toString() {
        return "TimeoutCustomizer{timeout=" + timeout + "ms}";
      }
    };
  }

  @Bean @Qualifier(QUALIFIER) Consumer<ClientFactoryBuilder> zipkinElasticsearchClientFactory(
    @Value("${zipkin.storage.elasticsearch.timeout:10000}") int timeout) {
    return new Consumer<ClientFactoryBuilder>() {
      @Override public void accept(ClientFactoryBuilder factory) {
        factory.connectTimeoutMillis(timeout);
      }

      @Override public String toString() {
        return "TimeoutCustomizer{timeout=" + timeout + "ms}";
      }
    };
  }


  @Bean @Qualifier(QUALIFIER) @Conditional(HttpLoggingSet.class)
  Consumer<HttpClientBuilder> zipkinElasticsearchHttpLogging(
    ZipkinElasticsearchStorageProperties es) {
    LoggingClientBuilder builder = new LoggingClientBuilder()
      .requestLogLevel(LogLevel.INFO)
      .successfulResponseLogLevel(LogLevel.INFO);

    switch (es.getHttpLogging()) {
      case HEADERS:
        builder.contentSanitizer(unused -> "");
        break;
      case BASIC:
        builder.contentSanitizer(unused -> "");
        builder.headersSanitizer(unused -> HttpHeaders.of());
        break;
      case BODY:
      default:
        break;
    }

    return new Consumer<HttpClientBuilder>() {
      @Override public void accept(HttpClientBuilder client) {
        client
          .decorator(builder.newDecorator())
          .decorator(
            es.getHttpLogging() == ZipkinElasticsearchStorageProperties.HttpLoggingLevel.BODY
            ? RawContentLoggingClient.newDecorator()
            : Function.identity());
      }

      @Override public String toString() {
        return "LoggingCustomizer{httpLogging=" + es.getHttpLogging() + "}";
      }
    };
  }

  @Bean @Qualifier(QUALIFIER) @Conditional(BasicAuthRequired.class)
  Consumer<HttpClientBuilder> zipkinElasticsearchHttpBasicAuth(
    ZipkinElasticsearchStorageProperties es) {
    return new Consumer<HttpClientBuilder>() {
      @Override public void accept(HttpClientBuilder client) {
        client.decorator(delegate -> new BasicAuthInterceptor(delegate, es));
      }

      @Override public String toString() {
        return "BasicAuthCustomizer{basicCredentials=<redacted>}";
      }
    };
  }

  @Bean @ConditionalOnMissingBean StorageComponent storage(
    ZipkinElasticsearchStorageProperties elasticsearch,
    @Qualifier(QUALIFIER) List<Consumer<HttpClientBuilder>> zipkinElasticsearchHttpCustomizers,
    @Qualifier(QUALIFIER) List<Consumer<ClientFactoryBuilder>>
      zipkinElasticsearchClientFactoryCustomizers,
    Optional<HostsSupplier> hostsSupplier,
    @Value("${zipkin.query.lookback:86400000}") int namesLookback,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys,
    @Value("${zipkin.storage.autocomplete-ttl:3600000}") int autocompleteTtl,
    @Value("${zipkin.storage.autocomplete-cardinality:20000}") int autocompleteCardinality) {
    ElasticsearchStorage.Builder result = elasticsearch
      .toBuilder()
      .clientCustomizer(new CompositeCustomizer<>(zipkinElasticsearchHttpCustomizers))
      .clientFactoryCustomizer(
        new CompositeCustomizer<>(zipkinElasticsearchClientFactoryCustomizers))
      .namesLookback(namesLookback)
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .autocompleteTtl(autocompleteTtl)
      .autocompleteCardinality(autocompleteCardinality);
    hostsSupplier.ifPresent(result::hostsSupplier);
    return result.build();
  }

  @Bean @Qualifier(QUALIFIER) @ConditionalOnSelfTracing
  Consumer<HttpClientBuilder> elasticsearchTracing(Optional<Tracing> tracing) {
    if (!tracing.isPresent()) {
      return client -> {
      };
    }
    return client -> client.decorator(BraveClient.newDecorator(tracing.get(), "elasticsearch"));
  }

  static final class HttpLoggingSet implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      return !isEmpty(
        condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.http-logging"));
    }
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

  static final class CompositeCustomizer<T> implements Consumer<T> {
    final List<Consumer<T>> customizers;

    CompositeCustomizer(List<Consumer<T>> customizers) {
      this.customizers = customizers;
    }

    @Override public void accept(T target) {
      for (Consumer<T> customizer : customizers) {
        customizer.accept(target);
      }
    }

    @Override public String toString() {
      return customizers.toString();
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
