/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zipkin2.server.internal.ZipkinActuatorImporter.PROPERTY_NAME_ACTUATOR_ENABLED;

// This tests actuator integration without actually requiring a compile dep on actuator
class ZipkinActuatorImporterTest {
  ZipkinActuatorImporter zipkinActuatorImporter =
    new ZipkinActuatorImporter(ActuatorImpl.class.getName());
  GenericApplicationContext context = new GenericApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesntCrashWhenNoIncludes() {
    zipkinActuatorImporter.initialize(context);

    context.refresh();
  }

  @Test void configuresInclude() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=" + Include1.class.getName()
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();
    context.getBean(Include1.class);
  }

  @Test void doesntCrashOnBadActuatorImpl() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=" + Include1.class.getName()
    ).applyTo(context);

    new ZipkinActuatorImporter("tomatoes").initialize(context);

    context.refresh();
    assertThatThrownBy(() -> context.getBean(Include1.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test void skipsWhenDisabled() {
    TestPropertyValues.of(
      PROPERTY_NAME_ACTUATOR_ENABLED + "=false",
      "zipkin.internal.actuator.include[1]=" + Include2.class.getName()
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();

    assertThatThrownBy(() -> context.getBean(Include1.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test void doesntCrashWhenBadInclude() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=tomatoes"
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();
  }

  @Test void configuresIncludes() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=" + Include1.class.getName(),
      "zipkin.internal.actuator.include[1]=" + Include2.class.getName()
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();
    context.getBean(Include1.class);
    context.getBean(Include2.class);
  }

  @Configuration
  static class ActuatorImpl {
  }

  @Configuration
  static class Include1 {
  }

  @Configuration
  static class Include2 {
  }
}
