/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.pulsar;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.pulsar.PulsarCollector;
import zipkin2.storage.StorageComponent;

import static io.micrometer.common.util.StringUtils.isEmpty;

/** Auto-configuration for {@link PulsarCollector}. */
@ConditionalOnClass(PulsarCollector.class)
@Conditional(ZipkinPulsarCollectorConfiguration.PulsarConditions.class)
@EnableConfigurationProperties(ZipkinPulsarCollectorProperties.class)
public class ZipkinPulsarCollectorConfiguration {

  @Bean(initMethod = "start")
  PulsarCollector pulsar(
      ZipkinPulsarCollectorProperties properties,
      CollectorSampler sampler,
      CollectorMetrics metrics,
      StorageComponent storage
  ) {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }

  /**
   * This condition passes when {@link ZipkinPulsarCollectorProperties#getServiceUrl()} is set
   * to non-empty.
   *
   * <p>This is here because the yaml defaults this property to empty like this, and spring-boot
   * doesn't have an option to treat empty properties as unset.
   *
   * <pre>{@code
   * service-url: ${PULSAR_SERVICE_URL:}
   * }</pre>
   */
  static final class PulsarConditions extends AllNestedConditions {

    PulsarConditions() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "zipkin.collector.pulsar", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    private static final class PulsarEnabledCondition {
    }

    @Conditional(PulsarServiceUrlCondition.class)
    private static final class PulsarServiceUrlCondition implements Condition {
      @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String serviceUrl = context.getEnvironment().getProperty("zipkin.collector.pulsar.service-url");
        return !isEmpty(serviceUrl);
      }
    }
  }
}
