/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.server;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.internal.v2.storage.InMemoryStorage;
import zipkin.internal.v2.storage.StorageComponent;

public class ZipkinServerV2StorageTest {
  AnnotationConfigApplicationContext context;

  @Before public void init() {
    context = new AnnotationConfigApplicationContext();
  }

  @After public void close() {
    if (context != null) context.close();
  }

  @Test public void adaptsStorageComponent() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      V2Storage.class,
      ZipkinServerConfiguration.class);
    context.refresh();

    context.getBean(zipkin.storage.StorageComponent.class);
  }

  @Configuration
  public static class V2Storage {
    @Bean public HealthAggregator healthAggregator() {
      return new OrderedHealthAggregator();
    }

    @Bean public StorageComponent component() {
      return InMemoryStorage.newBuilder().build();
    }
  }
}
