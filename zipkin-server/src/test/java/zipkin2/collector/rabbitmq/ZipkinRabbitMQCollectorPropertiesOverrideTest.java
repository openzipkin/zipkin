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
package zipkin2.collector.rabbitmq;

import java.net.URI;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.rabbitmq.Access;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ZipkinRabbitMQCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After
  public void close() {
    if (context != null) context.close();
  }

  @Parameterized.Parameter(0)
  public String property;

  @Parameterized.Parameter(1)
  public Object value;

  @Parameterized.Parameter(2)
  public Function<RabbitMQCollector.Builder, Object> builderExtractor;

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
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

  @Test
  public void propertyTransferredToCollectorBuilder() throws Exception {
    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerRabbitMQProperties(context);
    context.refresh();

    assertThat(Access.collectorBuilder(context))
        .extracting(builderExtractor)
        .isEqualTo(value);
  }
}
