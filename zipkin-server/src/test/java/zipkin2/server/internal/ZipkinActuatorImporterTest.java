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

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zipkin2.server.internal.ZipkinActuatorImporter.PROPERTY_NAME_ACTUATOR_ENABLED;

// This tests actuator integration without actually requiring a compile dep on actuator
public class ZipkinActuatorImporterTest {
  ZipkinActuatorImporter zipkinActuatorImporter =
    new ZipkinActuatorImporter(ActuatorImpl.class.getName());
  GenericApplicationContext context = new GenericApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test public void doesntCrashWhenNoIncludes() {
    zipkinActuatorImporter.initialize(context);

    context.refresh();
  }

  @Test public void configuresInclude() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=" + Include1.class.getName()
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();
    context.getBean(Include1.class);
  }

  @Test public void doesntCrashOnBadActuatorImpl() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=" + Include1.class.getName()
    ).applyTo(context);

    new ZipkinActuatorImporter("tomatoes").initialize(context);

    context.refresh();
    assertThatThrownBy(() -> context.getBean(Include1.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test public void skipsWhenDisabled() {
    TestPropertyValues.of(
      PROPERTY_NAME_ACTUATOR_ENABLED + "=false",
      "zipkin.internal.actuator.include[1]=" + Include2.class.getName()
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();

    assertThatThrownBy(() -> context.getBean(Include1.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test public void doesntCrashWhenBadInclude() {
    TestPropertyValues.of(
      "zipkin.internal.actuator.include[0]=tomatoes"
    ).applyTo(context);

    zipkinActuatorImporter.initialize(context);

    context.refresh();
  }

  @Test public void configuresIncludes() {
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
