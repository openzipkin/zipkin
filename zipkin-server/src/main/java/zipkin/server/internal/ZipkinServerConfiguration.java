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
package zipkin.server.internal;

import brave.Tracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.V2StorageComponent;
import zipkin.server.internal.brave.TracingStorageComponent;
import zipkin.server.internal.brave.TracingV2StorageComponent;
import zipkin.storage.StorageComponent;
import zipkin2.storage.InMemoryStorage;

@Configuration
public class ZipkinServerConfiguration {

  @Autowired(required = false) @Qualifier("httpTracingCustomizer")
  UndertowDeploymentInfoCustomizer httpTracingCustomizer;
  @Autowired(required = false) @Qualifier("httpRequestDurationCustomizer")
  UndertowDeploymentInfoCustomizer httpRequestDurationCustomizer;
  @Autowired(required = false)
  ZipkinHttpCollector httpCollector;

  /** Registers health for any components, even those not in this jar. */
  @Bean ZipkinHealthIndicator zipkinHealthIndicator(HealthAggregator healthAggregator) {
    return new ZipkinHealthIndicator(healthAggregator);
  }

  @Bean public UndertowServletWebServerFactory embeddedServletContainerFactory(
    @Value("${zipkin.query.allowed-origins:*}") String allowedOrigins
  ) {
    UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
    CorsHandler cors = new CorsHandler(allowedOrigins);
    if (httpCollector != null) {
      factory.addDeploymentInfoCustomizers(
        info -> info.addInitialHandlerChainWrapper(httpCollector)
      );
    }
    factory.addDeploymentInfoCustomizers(
      info -> info.addInitialHandlerChainWrapper(cors)
    );
    if (httpTracingCustomizer != null) {
      factory.addDeploymentInfoCustomizers(httpTracingCustomizer);
    }
    if (httpRequestDurationCustomizer != null) {
      factory.addDeploymentInfoCustomizers(httpRequestDurationCustomizer);
    }
    return factory;
  }

  @Bean
  @ConditionalOnMissingBean(CollectorSampler.class)
  CollectorSampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
    return CollectorSampler.create(rate);
  }

  @Bean
  @ConditionalOnMissingBean(CollectorMetrics.class)
  CollectorMetrics metrics(MeterRegistry registry) {
    return new ActuateCollectorMetrics(registry);
  }

  @Bean
  public MeterRegistryCustomizer meterRegistryCustomizer() {
    return registry -> registry.config()
      .meterFilter(MeterFilter.deny(id -> {
          String uri = id.getTag("uri");
          return uri != null
            && (uri.startsWith("/actuator")
            || uri.startsWith("/metrics")
            || uri.startsWith("/health")
            || uri.startsWith("/favicon.ico")
            || uri.startsWith("/prometheus")
          );
        })
      );
  }

  @Configuration
  @ConditionalOnSelfTracing
  static class TracingStorageComponentEnhancer implements BeanPostProcessor {

    @Autowired(required = false)
    Tracing tracing;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (tracing == null) return bean;
      if (bean instanceof V2StorageComponent) {
        return new TracingV2StorageComponent(tracing, (V2StorageComponent) bean);
      } else if (bean instanceof StorageComponent) {
        return new TracingStorageComponent(tracing, (StorageComponent) bean);
      }
      return bean;
    }
  }

  /**
   * This is a special-case configuration if there's no StorageComponent of any kind. In-Mem can
   * supply both read apis, so we add two beans here.
   */
  @Configuration
  @Conditional(StorageTypeMemAbsentOrEmpty.class)
  @ConditionalOnMissingBean(StorageComponent.class)
  static class InMemoryConfiguration {
    @Bean StorageComponent storage(
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
      @Value("${zipkin.storage.mem.max-spans:500000}") int maxSpans) {
      return V2StorageComponent.create(InMemoryStorage.newBuilder()
        .strictTraceId(strictTraceId)
        .searchEnabled(searchEnabled)
        .maxSpanCount(maxSpans)
        .build());
    }

    @Bean InMemoryStorage v2Storage(V2StorageComponent component) {
      return (InMemoryStorage) component.delegate();
    }
  }

  static final class StorageTypeMemAbsentOrEmpty implements Condition {
    @Override public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      String storageType = condition.getEnvironment().getProperty("zipkin.storage.type");
      if (storageType == null) return true;
      storageType = storageType.trim();
      if (storageType.isEmpty()) return true;
      return storageType.equals("mem");
    }
  }
}
