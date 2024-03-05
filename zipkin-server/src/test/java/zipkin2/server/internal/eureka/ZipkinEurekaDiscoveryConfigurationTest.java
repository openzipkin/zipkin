/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.eureka;

import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinEurekaDiscoveryConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideDiscoveryComponent_whenServiceUrlUnset() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinEurekaDiscoveryConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(EurekaUpdatingListener.class);
    });
  }

  @Test void doesNotProvidesEurekaUpdatingListener_whenServiceUrlEmptyString() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.discovery.eureka.service-url:").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinEurekaDiscoveryConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(EurekaUpdatingListener.class);
    });
  }

  @Test void providesDiscoveryComponent_whenServiceUrlSet() {
    TestPropertyValues.of("zipkin.discovery.eureka.service-url:http://localhost:8761/eureka/v2")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinEurekaDiscoveryConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(EurekaUpdatingListener.class)).isNotNull();
  }

  @Test void providesDiscoveryComponent_whenServiceUrlAuthenticates() {
    TestPropertyValues.of(
        "zipkin.discovery.eureka.service-url:http://myuser:mypassword@localhost:8761/eureka/v2")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinEurekaDiscoveryConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(EurekaUpdatingListener.class)).isNotNull();
  }

  @Test void doesNotProvidesEurekaUpdatingListener_whenServiceUrlSetAndDisabled() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.discovery.eureka.service-url:http://localhost:8761/eureka/v2")
        .applyTo(context);
      TestPropertyValues.of("zipkin.discovery.eureka.enabled:false").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinEurekaDiscoveryConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(EurekaUpdatingListener.class);
    });
  }

  @Test void canOverrideProperty_appName() {
    context = createContextWithOverridenProperty("zipkin.discovery.eureka.appName:zipkin-demo");

    assertThat(context.getBean(ZipkinEurekaDiscoveryProperties.class).getAppName())
      .isEqualTo("zipkin-demo");
  }

  @Test void canOverrideProperty_instanceId() {
    context = createContextWithOverridenProperty("zipkin.discovery.eureka.instanceId:zipkin-demo");

    assertThat(context.getBean(ZipkinEurekaDiscoveryProperties.class).getInstanceId())
      .isEqualTo("zipkin-demo");
  }

  @Test void canOverrideProperty_hostname() {
    context = createContextWithOverridenProperty("zipkin.discovery.eureka.hostname:zipkin-demo");

    assertThat(context.getBean(ZipkinEurekaDiscoveryProperties.class).getHostname())
      .isEqualTo("zipkin-demo");
  }

  private static AnnotationConfigApplicationContext createContextWithOverridenProperty(
    String pair) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of( // Add a service-url so that the pair is read.
      "zipkin.discovery.eureka.service-url:http://localhost:8761/eureka/v2",
      pair
    ).applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinEurekaDiscoveryConfiguration.class
    );
    context.refresh();
    return context;
  }
}
