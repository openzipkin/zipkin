/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin2.collector.kafka;

import java.util.Arrays;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.kafka.Access;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinKafkaCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach
  void close() {
    if (context != null) context.close();
  }

  public String property;
  public Object value;
  public Function<KafkaCollector.Builder, Object> builderExtractor;

  public static Iterable<Object[]> data() {
    return Arrays.asList(
      parameters(
        "bootstrap-servers",
        "127.0.0.1:9092",
        b -> b.properties.getProperty("bootstrap.servers")),
      parameters("group-id", "zapkin", b -> b.properties.getProperty("group.id")),
      parameters("topic", "zapkin", b -> b.topic),
      parameters("streams", 2, b -> b.streams),
      parameters(
        "overrides.auto.offset.reset",
        "latest",
        b -> b.properties.getProperty("auto.offset.reset")));
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(
    String propertySuffix, T value, Function<KafkaCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.kafka." + propertySuffix, value, builderExtractor};
  }

  @MethodSource("data")
  @ParameterizedTest(name = "{0}")
  void propertyTransferredToCollectorBuilder(String property, Object value,
    Function<KafkaCollector.Builder, Object> builderExtractor) {
    initZipkinKafkaCollectorPropertiesOverrideTest(property, value, builderExtractor);
    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerKafkaProperties(context);
    context.refresh();

    assertThat(Access.collectorBuilder(context))
      .extracting(builderExtractor)
      .isEqualTo(value);
  }

  void initZipkinKafkaCollectorPropertiesOverrideTest(String property, Object value,
    Function<KafkaCollector.Builder, Object> builderExtractor) {
    this.property = property;
    this.value = value;
    this.builderExtractor = builderExtractor;
  }
}
