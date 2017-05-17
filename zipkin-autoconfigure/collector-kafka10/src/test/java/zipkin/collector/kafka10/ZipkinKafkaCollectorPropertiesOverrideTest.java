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
package zipkin.collector.kafka10;

import java.util.Arrays;
import java.util.function.Function;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.collector.kafka10.ZipkinKafkaCollectorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

@RunWith(Parameterized.class)
public class ZipkinKafkaCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After
  public void close() {
    if (context != null) context.close();
  }

  @Parameterized.Parameter(0) public String property;
  @Parameterized.Parameter(1) public Object value;
  @Parameterized.Parameter(2) public Function<ZipkinKafkaCollectorProperties, Object>
      propertiesExtractor;
  @Parameterized.Parameter(3) public Function<KafkaCollector.Builder, Object> builderExtractor;

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        parameters("bootstrap-servers", "127.0.0.1:9092",
            ZipkinKafkaCollectorProperties::getBootstrapServers,
            b -> b.properties.getProperty("bootstrap.servers")),
        parameters("group-id", "zapkin",
            ZipkinKafkaCollectorProperties::getGroupId,
            b -> b.properties.getProperty("group.id")),
        parameters("topic", "zapkin",
            ZipkinKafkaCollectorProperties::getTopic,
            b -> b.topic),
        parameters("streams", 2,
            ZipkinKafkaCollectorProperties::getStreams,
            b -> b.streams),
        parameters("overrides.auto.offset.reset", "latest",
            p -> p.getOverrides().get("auto.offset.reset"),
            b -> b.properties.getProperty("auto.offset.reset"))
    );
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(String propertySuffix, T value,
      Function<ZipkinKafkaCollectorProperties, T> propertiesExtractor,
      Function<KafkaCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.kafka." + propertySuffix, value, propertiesExtractor,
        builderExtractor};
  }

  @Test
  public void canOverrideValueOf() {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableKafkaCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinKafkaCollectorProperties.class))
        .extracting(propertiesExtractor)
        .containsExactly(value);
  }

  @Test
  public void propertyTransferredToCollectorBuilder() {
    addEnvironment(context, property + ":" + value);

    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        EnableKafkaCollectorProperties.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinKafkaCollectorProperties.class).toBuilder())
        .extracting(builderExtractor)
        .containsExactly(value);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinKafkaCollectorProperties.class)
  static class EnableKafkaCollectorProperties {
  }
}
