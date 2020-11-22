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
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

public class ZipkinModuleImporterTest {
  ZipkinModuleImporter zipkinModuleImporter = new ZipkinModuleImporter();
  GenericApplicationContext context = new GenericApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test public void doesntCrashWhenNoModules() {
    zipkinModuleImporter.initialize(context);

    context.refresh();
  }

  @Test public void configuresModule() {
    TestPropertyValues.of(
      "zipkin.internal.module.module1=" + Module1.class.getName()
    ).applyTo(context);

    zipkinModuleImporter.initialize(context);

    context.refresh();
    context.getBean(Module1.class);
  }

  @Test public void doesntCrashWhenBadModule() {
    TestPropertyValues.of(
      "zipkin.internal.module.module1=tomatoes"
    ).applyTo(context);

    zipkinModuleImporter.initialize(context);

    context.refresh();
  }

  @Test public void configuresModules() {
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
