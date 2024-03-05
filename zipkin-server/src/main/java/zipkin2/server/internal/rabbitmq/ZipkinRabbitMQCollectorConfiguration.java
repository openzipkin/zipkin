/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.rabbitmq;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.rabbitmq.RabbitMQCollector;
import zipkin2.storage.StorageComponent;

/** Auto-configuration for {@link RabbitMQCollector}. */
@ConditionalOnClass(RabbitMQCollector.class)
@Conditional(ZipkinRabbitMQCollectorConfiguration.RabbitMQAddressesOrUriSet.class)
@EnableConfigurationProperties(ZipkinRabbitMQCollectorProperties.class)
public class ZipkinRabbitMQCollectorConfiguration {

  @Bean(initMethod = "start")
  RabbitMQCollector rabbitMq(
      ZipkinRabbitMQCollectorProperties properties,
      CollectorSampler sampler,
      CollectorMetrics metrics,
      StorageComponent storage)
      throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }
  /**
   * This condition passes when {@link ZipkinRabbitMQCollectorProperties#getAddresses()} or {@link
   * ZipkinRabbitMQCollectorProperties#getUri()} is set to a non-empty value.
   *
   * <p>This is here because the yaml defaults this property to empty like this, and Spring Boot
   * doesn't have an option to treat empty properties as unset.
   *
   * <pre>{@code
   * addresses: ${RABBIT_ADDRESSES:}
   * uri: ${RABBIT_URI:}
   * }</pre>
   */
  static final class RabbitMQAddressesOrUriSet implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata a) {
      return (!isEmpty(context.getEnvironment().getProperty("zipkin.collector.rabbitmq.addresses"))
        || !isEmpty(context.getEnvironment().getProperty("zipkin.collector.rabbitmq.uri"))) &&
        notFalse(context.getEnvironment().getProperty("zipkin.collector.rabbitmq.enabled"));
    }

    private static boolean isEmpty(String s) {
      return s == null || s.isEmpty();
    }

    private static boolean notFalse(String s){
      return s == null || !s.equals("false");
    }
  }
}
