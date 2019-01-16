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
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.annotation.Options;
import com.linecorp.armeria.server.cors.CorsService;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.server.tomcat.TomcatService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;
import java.util.function.Function;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.server.internal.brave.TracingStorageComponent;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

@Configuration
public class ZipkinServerConfiguration implements WebMvcConfigurer {

  @Autowired(required = false)
  ZipkinQueryApiV2 httpQuery;

  @Autowired(required = false)
  ZipkinHttpCollector httpCollector;

  @Autowired(required = false)
  ServletWebServerApplicationContext webServerContext;
  /**
   * Extracts a Tomcat {@link Connector} from Spring webapp context.
   */
  public static Connector getConnector(ServletWebServerApplicationContext applicationContext) {
    final TomcatWebServer container = (TomcatWebServer) applicationContext.getWebServer();

    // Start the container to make sure all connectors are available.
    container.start();
    return container.getTomcat().getConnector();
  }


  @Bean ArmeriaServerConfigurator serverConfigurator(MetricsHealthController healthController) {
    return sb -> {
      if (httpQuery != null) {
        sb.annotatedService(httpQuery);
        // For UI.
        sb.annotatedService("/zipkin", httpQuery);
      }
      if (httpCollector != null) sb.annotatedService(httpCollector);
      sb.annotatedService(healthController);
      // Redirects the prometheus scrape endpoint for backward compatibility
      sb.service("/prometheus", new RedirectService("/actuator/prometheus/"));
      if (webServerContext != null) {
        sb.serviceUnder("/", TomcatService.forConnector(getConnector(webServerContext)));
      }
    };
  }

  /** Registers health for any components, even those not in this jar. */
  @Bean
  ZipkinHealthIndicator zipkinHealthIndicator(HealthAggregator healthAggregator) {
    return new ZipkinHealthIndicator(healthAggregator);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/info", "/actuator/info");
  }

  @Bean ArmeriaServerConfigurator corsConfigurator(
    @Value("${zipkin.query.allowed-origins:*}") String allowedOrigins) {
    CorsServiceBuilder corsBuilder = allowedOrigins.equals("*") ? CorsServiceBuilder.forAnyOrigin()
      : CorsServiceBuilder.forOrigins(allowedOrigins.split(","));

    Function<Service<HttpRequest, HttpResponse>, CorsService>
      corsDecorator = corsBuilder.allowRequestMethods(HttpMethod.GET).newDecorator();

    return server -> server
      .annotatedService(new Object() { // don't know how else to enable options for CORS preflight!
        @Options("glob:/**")
        public void options() {
        }
      })
      .decorator(corsDecorator);
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
   */
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
