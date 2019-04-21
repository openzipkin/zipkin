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
package zipkin2.server.internal.cassandra;

import brave.Tracing;
import brave.cassandra.driver.TracingSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.cassandra.v1.SessionFactory;

/**
 * This storage accepts Cassandra logs in a specified category. Each log entry is expected to
 * contain a single span, which is TBinaryProtocol big-endian, then base64 encoded. Decoded spans
 * are stored asynchronously.
 */
@Configuration
@EnableConfigurationProperties(ZipkinCassandraStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "cassandra")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinCassandraStorageConfiguration {

  @Bean SessionFactory sessionFactory() {
    return new SessionFactory.Default();
  }

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(
      ZipkinCassandraStorageProperties properties,
      SessionFactory sessionFactory,
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
      @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys,
      @Value("${zipkin.storage.autocomplete-ttl:3600000}") int autocompleteTtl,
      @Value("${zipkin.storage.autocomplete-cardinality:20000}") int autocompleteCardinality) {
   return properties.toBuilder()
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .autocompleteTtl(autocompleteTtl)
      .autocompleteCardinality(autocompleteCardinality)
      .sessionFactory(sessionFactory).build();
  }

  @Configuration
  @ConditionalOnSelfTracing
  static class TracingSessionFactoryEnhancer implements BeanPostProcessor {

    @Autowired(required = false) Tracing tracing;

    @Override public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
    }

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (tracing == null) return bean;
      if (bean instanceof SessionFactory) {
        SessionFactory delegate = (SessionFactory) bean;
        return (SessionFactory) storage -> TracingSession.create(tracing, delegate.create(storage));
      }
      return bean;
    }
  }
}
