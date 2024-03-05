/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import brave.Tracing;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.server.internal.brave.TracingStorageComponent;
import zipkin2.server.internal.throttle.ThrottledStorageComponent;
import zipkin2.server.internal.throttle.ZipkinStorageThrottleProperties;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

/** Base collector and storage configurations needed for higher-level integrations */
@Import({
  ZipkinConfiguration.InMemoryConfiguration.class,
  ZipkinConfiguration.ThrottledStorageComponentEnhancer.class,
  ZipkinConfiguration.TracingStorageComponentEnhancer.class
})
public class ZipkinConfiguration {

  @Bean CollectorSampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
    return CollectorSampler.create(rate);
  }

  @Bean CollectorMetrics metrics(MeterRegistry registry) {
    return new MicrometerCollectorMetrics(registry);
  }

  @EnableConfigurationProperties(ZipkinStorageThrottleProperties.class)
  @ConditionalOnThrottledStorage
  static class ThrottledStorageComponentEnhancer implements BeanPostProcessor, BeanFactoryAware {

    /**
     * Need this to resolve cyclic instantiation issue with spring when instantiating with metrics
     * and tracing.
     *
     * <p>Ref: <a href="https://stackoverflow.com/a/19688634">Tracking down cause of Spring's "not
     * eligible for auto-proxying"</a></p>
     */
    BeanFactory beanFactory;

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof StorageComponent component) {
        ZipkinStorageThrottleProperties throttleProperties =
          beanFactory.getBean(ZipkinStorageThrottleProperties.class);
        return new ThrottledStorageComponent(component,
          beanFactory.getBean(MeterRegistry.class),
          beanFactory.containsBean("tracing") ? beanFactory.getBean(Tracing.class) : null,
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

  @ConditionalOnSelfTracing
  static class TracingStorageComponentEnhancer implements BeanPostProcessor, BeanFactoryAware {
    /**
     * Need this to resolve cyclic instantiation issue with spring when instantiating with tracing.
     *
     * <p>Ref: <a href="https://stackoverflow.com/a/19688634">Tracking down cause of Spring's "not
     * eligible for auto-proxying"</a></p>
     */
    BeanFactory beanFactory;

    @Override public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
    }

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof StorageComponent component && beanFactory.containsBean("tracing")) {
        Tracing tracing = beanFactory.getBean(Tracing.class);
        return new TracingStorageComponent(tracing, component);
      }
      return bean;
    }

    @Override public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      this.beanFactory = beanFactory;
    }
  }

  /**
   * This is a special-case configuration if there's no StorageComponent of any kind. In-Mem can
   * supply both read apis, so we add two beans here.
   */
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
