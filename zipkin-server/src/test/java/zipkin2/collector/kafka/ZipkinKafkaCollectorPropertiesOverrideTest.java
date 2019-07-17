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
package zipkin2.collector.kafka;

import java.util.Arrays;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.kafka.Access;

@RunWith(Parameterized.class)
public class ZipkinKafkaCollectorPropertiesOverrideTest {

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
  public Function<KafkaCollector.Builder, Object> builderExtractor;

  @Parameterized.Parameters(name = "{0}")
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

  @Test
  public void propertyTransferredToCollectorBuilder() {
    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerKafkaProperties(context);
    context.refresh();

    Assertions.assertThat(Access.collectorBuilder(context))
        .extracting(builderExtractor)
        .isEqualTo(value);
  }
}
