/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pulsar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.pulsar.Access;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinPulsarCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    if (context != null) context.close();
  }

  public String property;
  public Object value;
  public Function<PulsarCollector.Builder, Object> builderExtractor;

  public static Iterable<Object[]> data() {
    return List.of(
        // intentionally punting on comma-separated form of a list of addresses as it doesn't fit
        // this unit test. Better to make a separate one than force-fit!
        parameters("service-url", "pulsar://127.0.0.1:6650", b -> b.clientProps.get("serviceUrl")),
        parameters("topic", "zipkin", b -> b.topic),
        parameters("concurrency", 2, b -> b.concurrency),
        parameters("clientProps.serviceUrl", "pulsar://127.0.0.1:6650", b -> b.clientProps.get("serviceUrl")),
        parameters("consumerProps.subscriptionName", "zipkin-subscription", b -> b.consumerProps.get("subscriptionName"))
    );
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(
      String propertySuffix, T value, Function<PulsarCollector.Builder, T> builderExtractor) {
    return new Object[]{"zipkin.collector.pulsar." + propertySuffix, value, builderExtractor};
  }

  @MethodSource("data")
  @ParameterizedTest(name = "{0}")
  void propertyTransferredToCollectorBuilder(String property, Object value,
                                             Function<PulsarCollector.Builder, Object> builderExtractor) throws Exception {
    initZipkinPulsarCollectorPropertiesOverrideTest(property, value, builderExtractor);
    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerPulsarProperties(context);
    context.refresh();

    assertThat(Access.collectorBuilder(context))
        .extracting(builderExtractor)
        .isEqualTo(value);
  }

  void initZipkinPulsarCollectorPropertiesOverrideTest(String property, Object value,
                                                       Function<PulsarCollector.Builder, Object> builderExtractor) {
    this.property = property;
    this.value = value;
    this.builderExtractor = builderExtractor;
  }
}
