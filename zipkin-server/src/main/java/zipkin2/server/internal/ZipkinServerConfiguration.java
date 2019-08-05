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
package zipkin2.server.internal;

import brave.Tracing;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.server.internal.brave.TracingStorageComponent;
import zipkin2.server.internal.throttle.ThrottledStorageComponent;
import zipkin2.server.internal.throttle.ZipkinStorageThrottleProperties;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

@Configuration
@ImportAutoConfiguration(ArmeriaSpringActuatorAutoConfiguration.class)
public class ZipkinServerConfiguration implements WebMvcConfigurer {

  @Autowired(required = false)
  ZipkinQueryApiV2 httpQuery;

  @Autowired(required = false)
  ZipkinHttpCollector httpCollector;

  @Autowired(required = false)
  MetricsHealthController healthController;

  @Bean ArmeriaServerConfigurator serverConfigurator() {
    return sb -> {
      if (httpQuery != null) {
        sb.annotatedService(httpQuery);
        sb.annotatedService("/zipkin", httpQuery); // For UI.
      }
      if (httpCollector != null) sb.annotatedService(httpCollector);
      if (healthController != null) sb.annotatedService(healthController);
      // Redirects the prometheus scrape endpoint for backward compatibility
      sb.service("/prometheus", new RedirectService("/actuator/prometheus"));
      // Redirects the info endpoint for backward compatibility
      sb.service("/info", new RedirectService("/actuator/info"));

      // It's common for backend requests to have timeouts of the magic number 10s, so we go ahead
      // and default to a slightly longer timeout on the server to be able to handle these with
      // better error messages where possible.
      sb.requestTimeout(Duration.ofSeconds(11));
    };
  }

  /** Registers health for any components, even those not in this jar. */
  @Bean
  ZipkinHealthIndicator zipkinHealthIndicator(HealthAggregator healthAggregator) {
    return new ZipkinHealthIndicator(healthAggregator);
  }

  @Override public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/info", "/actuator/info");
  }

  /** Configures the server at the last because of the specified {@link Order} annotation. */
  @Order @Bean ArmeriaServerConfigurator corsConfigurator(
    @Value("${zipkin.query.allowed-origins:*}") String allowedOrigins) {
    CorsServiceBuilder corsBuilder = CorsServiceBuilder.forOrigins(allowedOrigins.split(","))
      // NOTE: The property says query, and the UI does not use POST, but we allow POST?
      //
      // The reason is that our former CORS implementation accidentally allowed POST. People doing
      // browser-based tracing relied on this, so we can't remove it by default. In the future, we
      // could split the collector's CORS policy into a different property, still allowing POST
      // with content-type by default.
      .allowRequestMethods(HttpMethod.GET, HttpMethod.POST)
      .allowRequestHeaders(HttpHeaderNames.CONTENT_TYPE,
        // Use literals to avoid a runtime dependency on armeria-grpc types
        HttpHeaderNames.of("X-GRPC-WEB"))
      .exposeHeaders("grpc-status", "grpc-message", "armeria.grpc.ThrowableProto-bin");
    return builder -> builder.decorator(corsBuilder::build);
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
    return registry ->
      registry
        .config()
        .meterFilter(
          MeterFilter.deny(
            id -> {
              String uri = id.getTag("uri");
              return uri != null
                && (uri.startsWith("/actuator")
                || uri.startsWith("/metrics")
                || uri.startsWith("/health")
                || uri.startsWith("/favicon.ico")
                || uri.startsWith("/prometheus"));
            }));
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinStorageThrottleProperties.class)
  @ConditionalOnThrottledStorage
  static class ThrottledStorageComponentEnhancer implements BeanPostProcessor, BeanFactoryAware {
    @Autowired(required = false)
    Tracing tracing;

    /**
     * Need this to resolve cyclic instantiation issue with spring.  Mostly, this is for
     * MeterRegistry as really bad things happen if you try to Autowire it (loss of JVM metrics) but
     * also using it for properties just to make sure no cycles exist at all as a result of turning
     * throttling on.
     *
     * <p>Ref: <a href="https://stackoverflow.com/a/19688634">Tracking down cause of Spring's "not
     * eligible for auto-proxying"</a></p>
     */
    private BeanFactory beanFactory;

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof StorageComponent) {
        ZipkinStorageThrottleProperties throttleProperties =
          beanFactory.getBean(ZipkinStorageThrottleProperties.class);
        return new ThrottledStorageComponent((StorageComponent) bean,
          beanFactory.getBean(MeterRegistry.class),
          tracing,
          throttleProperties.getMinConcurrency(),
          throttleProperties.getMaxConcurrency(),
          throttleProperties.getMaxQueueSize());
      }
      return bean;
    }

    @Override public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      this.beanFactory = beanFactory;
    }
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
      if (bean instanceof StorageComponent) {
        return new TracingStorageComponent(tracing, (StorageComponent) bean);
      }
      return bean;
    }
  }

  /**
   * This is a special-case configuration if there's no StorageComponent of any kind. In-Mem can
   * supply both read apis, so we add two beans here.
   *
   * <p>Note: this needs to be {@link Lazy} to avoid circular dependency issues when using with
   * {@link ThrottledStorageComponentEnhancer}.
   */
  @Lazy
  @Configuration
  @Conditional(StorageTypeMemAbsentOrEmpty.class)
  @ConditionalOnMissingBean(StorageComponent.class)
  static class InMemoryConfiguration {
    @Bean
    StorageComponent storage(
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
      @Value("${zipkin.storage.mem.max-spans:500000}") int maxSpans,
      @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys) {
      return InMemoryStorage.newBuilder()
        .strictTraceId(strictTraceId)
        .searchEnabled(searchEnabled)
        .maxSpanCount(maxSpans)
        .autocompleteKeys(autocompleteKeys)
        .build();
    }
  }

  static final class StorageTypeMemAbsentOrEmpty implements Condition {
    @Override
    public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      String storageType = condition.getEnvironment().getProperty("zipkin.storage.type");
      if (storageType == null) return true;
      storageType = storageType.trim();
      if (storageType.isEmpty()) return true;
      return storageType.equals("mem");
    }
  }
}
