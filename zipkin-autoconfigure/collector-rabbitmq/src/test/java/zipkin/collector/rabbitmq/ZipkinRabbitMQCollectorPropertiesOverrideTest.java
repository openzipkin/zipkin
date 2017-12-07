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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.collector.rabbitmq.ZipkinRabbitMQCollectorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

@RunWith(Parameterized.class)
public class ZipkinRabbitMQCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After
  public void close() {
    if (context != null) context.close();
  }

  @Parameterized.Parameter(0) public String property;
  @Parameterized.Parameter(1) public Object value;
  @Parameterized.Parameter(2) public Function<ZipkinRabbitMQCollectorProperties, Object>
      propertiesExtractor;
  @Parameterized.Parameter(3) public Function<RabbitMQCollector.Builder, Object> builderExtractor;

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        // intentionally punting on comma-separated form of a list of addresses as it doesn't fit
        // this unit test. Better to make a separate one than force-fit!
        parameters("addresses", "localhost:5671",
            properties -> convertToStringWithoutListBrackets(properties.getAddresses()),
            builder -> builder.addresses[0].toString()),
        parameters("concurrency", 2,
            ZipkinRabbitMQCollectorProperties::getConcurrency,
            builder -> builder.concurrency),
        parameters("connectionTimeout", 30_000,
            ZipkinRabbitMQCollectorProperties::getConnectionTimeout,
            builder -> builder.connectionFactory.getConnectionTimeout()),
        parameters("password", "admin",
            ZipkinRabbitMQCollectorProperties::getPassword,
            builder -> builder.connectionFactory.getPassword()),
        parameters("queue", "zapkin",
            ZipkinRabbitMQCollectorProperties::getQueue,
            builder -> builder.queue),
        parameters("username", "admin",
            ZipkinRabbitMQCollectorProperties::getUsername,
            builder -> builder.connectionFactory.getUsername()),
        parameters("virtualHost", "/hello",
            ZipkinRabbitMQCollectorProperties::getVirtualHost,
            builder -> builder.connectionFactory.getVirtualHost()),
        parameters("useSsl", true,
          ZipkinRabbitMQCollectorProperties::getUseSsl,
          builder -> builder.connectionFactory.isSSL())
    );
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(String propertySuffix, T value,
      Function<ZipkinRabbitMQCollectorProperties, T> propertiesExtractor,
      Function<RabbitMQCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.rabbitmq." + propertySuffix, value, propertiesExtractor,
        builderExtractor};
  }

  @Test
  public void canOverrideValueOf() {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableRabbitMQCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinRabbitMQCollectorProperties.class))
        .extracting(propertiesExtractor)
        .containsExactly(value);
  }

  @Test
  public void propertyTransferredToCollectorBuilder()
    throws NoSuchAlgorithmException, KeyManagementException {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableRabbitMQCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinRabbitMQCollectorProperties.class).toBuilder())
        .extracting(builderExtractor)
        .containsExactly(value);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinRabbitMQCollectorProperties.class)
  static class EnableRabbitMQCollectorProperties {
  }

  private static String convertToStringWithoutListBrackets(List<String> list) {
    return list.toString().substring(1, list.toString().length() - 1).replaceAll(" ", "");
  }
}
