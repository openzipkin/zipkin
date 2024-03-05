/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

class ZipkinModuleImporterTest {
  ZipkinModuleImporter zipkinModuleImporter = new ZipkinModuleImporter();
  GenericApplicationContext context = new GenericApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesntCrashWhenNoModules() {
    zipkinModuleImporter.initialize(context);

    context.refresh();
  }

  @Test void configuresModule() {
    TestPropertyValues.of(
      "zipkin.internal.module.module1=" + Module1.class.getName()
    ).applyTo(context);

    zipkinModuleImporter.initialize(context);

    context.refresh();
    context.getBean(Module1.class);
  }

  @Test void doesntCrashWhenBadModule() {
    TestPropertyValues.of(
      "zipkin.internal.module.module1=tomatoes"
    ).applyTo(context);

    zipkinModuleImporter.initialize(context);

    context.refresh();
  }

  @Test void configuresModules() {
    TestPropertyValues.of(
      "zipkin.internal.module.module1=" + Module1.class.getName(),
      "zipkin.internal.module.module2=" + Module2.class.getName()
    ).applyTo(context);

    zipkinModuleImporter.initialize(context);

    context.refresh();
    context.getBean(Module1.class);
    context.getBean(Module2.class);
  }

  @Configuration
  static class Module1 {
  }

  @Configuration
  static class Module2 {
  }
}
