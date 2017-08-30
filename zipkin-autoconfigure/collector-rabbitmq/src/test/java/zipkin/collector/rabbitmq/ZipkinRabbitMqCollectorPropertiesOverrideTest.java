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
package zipkin.collector.rabbitmq;

import com.rabbitmq.client.Address;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.collector.rabbitmq.ZipkinRabbitMqCollectorProperties;
import zipkin.collector.rabbitmq.RabbitMqCollector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

@RunWith(Parameterized.class)
public class ZipkinRabbitMqCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After
  public void close() {
    if (context != null) context.close();
  }

  @Parameterized.Parameter(0) public String property;
  @Parameterized.Parameter(1) public Object value;
  @Parameterized.Parameter(2) public Function<ZipkinRabbitMqCollectorProperties, Object>
      propertiesExtractor;
  @Parameterized.Parameter(3) public Function<RabbitMqCollector.Builder, Object> builderExtractor;

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        parameters("addresses", "localhost:5671,localhost:5673",
          properties -> convertToStringWithoutListBrackets(properties.getAddresses()),
            builder -> convertToStringWithoutListBrackets(builder.addresses)),
      parameters("concurrency", 2,
            ZipkinRabbitMqCollectorProperties::getConcurrency,
            builder -> builder.concurrency),
        parameters("connectionTimeout", 30_000,
            ZipkinRabbitMqCollectorProperties::getConnectionTimeout,
            builder -> builder.connectionFactory.getConnectionTimeout()),
        parameters("password", "admin",
            ZipkinRabbitMqCollectorProperties::getPassword,
            builder -> builder.connectionFactory.getPassword()),
        parameters("queue", "zapkin",
            ZipkinRabbitMqCollectorProperties::getQueue,
            builder -> builder.queue),
        parameters("username", "admin",
            ZipkinRabbitMqCollectorProperties::getUsername,
            builder -> builder.connectionFactory.getUsername()),
        parameters("virtualHost", "/hello",
            ZipkinRabbitMqCollectorProperties::getVirtualHost,
            builder -> builder.connectionFactory.getVirtualHost())
    );
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(String propertySuffix, T value,
      Function<ZipkinRabbitMqCollectorProperties, T> propertiesExtractor,
      Function<RabbitMqCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.rabbitmq." + propertySuffix, value, propertiesExtractor,
        builderExtractor};
  }

  @Test
  public void canOverrideValueOf() {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableRabbitMqCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinRabbitMqCollectorProperties.class))
        .extracting(propertiesExtractor)
        .containsExactly(value);
  }

  @Test
  public void propertyTransferredToCollectorBuilder() {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableRabbitMqCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinRabbitMqCollectorProperties.class).toBuilder())
        .extracting(builderExtractor)
        .containsExactly(value);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinRabbitMqCollectorProperties.class)
  static class EnableRabbitMqCollectorProperties {
  }

  private static String convertToStringWithoutListBrackets(List<String> list) {
    return list.toString().substring(1, list.toString().length() - 1).replaceAll(" ", "");
  }
}
