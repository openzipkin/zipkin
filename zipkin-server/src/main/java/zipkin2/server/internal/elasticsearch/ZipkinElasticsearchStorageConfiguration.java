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
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.brave.BraveClient;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;
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
import zipkin2.elasticsearch.internal.BasicAuthInterceptor;
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinElasticsearchStorageConfiguration {
  static final Logger LOG = Logger.getLogger(ElasticsearchStorage.class.getName());
  static final String QUALIFIER = "zipkinElasticsearchHttp";

  @Bean @Qualifier(QUALIFIER) Consumer<ClientOptionsBuilder> zipkinElasticsearchHttp(
    @Value("${zipkin.storage.elasticsearch.timeout:10000}") int timeout) {
    return new Consumer<ClientOptionsBuilder>() {
      @Override public void accept(ClientOptionsBuilder client) {
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

  @Bean @Qualifier(QUALIFIER) @Conditional(BasicAuthRequired.class)
  Consumer<ClientOptionsBuilder> zipkinElasticsearchHttpBasicAuth(
    ZipkinElasticsearchStorageProperties es) {
    return new Consumer<ClientOptionsBuilder>() {
      @Override public void accept(ClientOptionsBuilder client) {
        client.decorator(
          delegate -> BasicAuthInterceptor.create(delegate, es.getUsername(), es.getPassword()));
      }

      @Override public String toString() {
        return "BasicAuthCustomizer{basicCredentials=<redacted>}";
      }
    };
  }

  @Bean @ConditionalOnMissingBean HostsSupplier hostsSupplier(
    ZipkinElasticsearchStorageProperties props) {
    String hosts = Optional.ofNullable(props.getHosts()).orElse("http://localhost:9200");
    List<String> hostList = new ArrayList<>();
    for (String host : hosts.split(",", 100)) {
      if (host.startsWith("http://") || host.startsWith("https://")) {
        hostList.add(host);
        continue;
      }
      final int port;
      try {
        port = new URL("http://" + host).getPort();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Malformed elasticsearch host: " + host);
      }
      if (port == -1) {
        host += ":9200";
      } else if (port == 9300) {
        LOG.warning(
          "Native transport no longer supported. Changing " + host + " to http port 9200");
        host = host.replace(":9300", ":9200");
      }
      hostList.add("http://" + host);
    }
    return new HostsSupplier() {
      @Override public List<String> get() {
        return hostList;
      }

      @Override public String toString() {
        return hostList.toString();
      }
    };
  }

  @Bean @ConditionalOnMissingBean StorageComponent storage(
    ZipkinElasticsearchStorageProperties elasticsearch,
    @Qualifier(QUALIFIER) List<Consumer<ClientOptionsBuilder>> zipkinElasticsearchHttpCustomizers,
    @Qualifier(QUALIFIER) List<Consumer<ClientFactoryBuilder>>
      zipkinElasticsearchClientFactoryCustomizers,
    HostsSupplier hostsSupplier,
    @Value("${zipkin.query.lookback:86400000}") int namesLookback,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys,
    @Value("${zipkin.storage.autocomplete-ttl:3600000}") int autocompleteTtl,
    @Value("${zipkin.storage.autocomplete-cardinality:20000}") int autocompleteCardinality) {
    ElasticsearchStorage.Builder builder = elasticsearch
      .toBuilder()
      .hostsSupplier(hostsSupplier)
      .clientCustomizer(new CompositeCustomizer<>(zipkinElasticsearchHttpCustomizers))
      .clientFactoryCustomizer(
        new CompositeCustomizer<>(zipkinElasticsearchClientFactoryCustomizers))
      .namesLookback(namesLookback)
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .autocompleteTtl(autocompleteTtl)
      .autocompleteCardinality(autocompleteCardinality);

    return builder.build();
  }

  @Bean @Qualifier(QUALIFIER) @ConditionalOnSelfTracing Consumer<ClientOptionsBuilder>
  elasticsearchTracing(Optional<Tracing> tracing) {
    if (!tracing.isPresent()) {
      return client -> {};
    }
    return client -> client.decorator(BraveClient.newDecorator(tracing.get(), "elasticsearch"));
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
