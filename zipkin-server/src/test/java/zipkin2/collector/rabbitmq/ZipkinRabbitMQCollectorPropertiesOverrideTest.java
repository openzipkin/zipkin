/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.rabbitmq;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.rabbitmq.Access;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinRabbitMQCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    if (context != null) context.close();
  }

  public String property;
  public Object value;
  public Function<RabbitMQCollector.Builder, Object> builderExtractor;

  public static Iterable<Object[]> data() {
    return List.of(
        // intentionally punting on comma-separated form of a list of addresses as it doesn't fit
        // this unit test. Better to make a separate one than force-fit!
        parameters("addresses", "localhost:5671", builder -> builder.addresses[0].toString()),
        parameters("concurrency", 2, builder -> builder.concurrency),
        parameters(
            "connectionTimeout",
            30_000,
            builder -> builder.connectionFactory.getConnectionTimeout()),
        parameters("password", "admin", builder -> builder.connectionFactory.getPassword()),
        parameters("queue", "zapkin", builder -> builder.queue),
        parameters("username", "admin", builder -> builder.connectionFactory.getUsername()),
        parameters("virtualHost", "/hello", builder -> builder.connectionFactory.getVirtualHost()),
        parameters("useSsl", true, builder -> builder.connectionFactory.isSSL()),
        parameters(
            "uri",
            URI.create("amqp://localhost"),
            builder -> URI.create("amqp://" + builder.connectionFactory.getHost())));
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(
      String propertySuffix, T value, Function<RabbitMQCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.rabbitmq." + propertySuffix, value, builderExtractor};
  }

  @MethodSource("data")
  @ParameterizedTest(name = "{0}")
  void propertyTransferredToCollectorBuilder(String property, Object value,
    Function<RabbitMQCollector.Builder, Object> builderExtractor) throws Exception {
    initZipkinRabbitMQCollectorPropertiesOverrideTest(property, value, builderExtractor);
    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerRabbitMQProperties(context);
    context.refresh();

    assertThat(Access.collectorBuilder(context))
        .extracting(builderExtractor)
        .isEqualTo(value);
  }

  void initZipkinRabbitMQCollectorPropertiesOverrideTest(String property, Object value,
    Function<RabbitMQCollector.Builder, Object> builderExtractor) {
    this.property = property;
    this.value = value;
    this.builderExtractor = builderExtractor;
  }
}
