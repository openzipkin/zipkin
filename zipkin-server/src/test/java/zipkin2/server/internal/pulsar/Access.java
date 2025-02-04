/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.pulsar;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.pulsar.PulsarCollector;

/** opens package access for testing */
public final class Access {

  /** Just registering properties to avoid automatically connecting to a Pulsar server */
  public static void registerPulsarProperties(AnnotationConfigApplicationContext context) {
    context.register(
        PropertyPlaceholderAutoConfiguration.class, EnablePulsarCollectorProperties.class);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinPulsarCollectorProperties.class)
  static class EnablePulsarCollectorProperties {
  }

  public static PulsarCollector.Builder collectorBuilder(
      AnnotationConfigApplicationContext context) {
    return context.getBean(ZipkinPulsarCollectorProperties.class).toBuilder();
  }
}
