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
package zipkin2.collector.activemq;

import java.util.Arrays;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.activemq.Access;

@RunWith(Parameterized.class)
public class ZipkinActiveMQCollectorPropertiesOverrideTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    context.close();
  }

  @Parameter(0)
  public String property;

  @Parameter(1)
  public Object value;

  @Parameter(2)
  public Function<ActiveMQCollector.Builder, Object> builderExtractor;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
      parameters("url", "failover:(tcp://localhost:61616,tcp://remotehost:61616)",
        b -> b.connectionFactory.getBrokerURL()),
      parameters("client-id-prefix", "zipkin-prod", b -> b.connectionFactory.getClientIDPrefix()),
      parameters("queue", "zapkin", b -> b.queue),
      parameters("concurrency", 2, b -> b.concurrency),
      parameters("username", "u", b -> b.connectionFactory.getUserName()),
      parameters("password", "p", b -> b.connectionFactory.getPassword())
    );
  }

  /** to allow us to define with a lambda */
  static <T> Object[] parameters(
    String propertySuffix, T value, Function<ActiveMQCollector.Builder, T> builderExtractor) {
    return new Object[] {"zipkin.collector.activemq." + propertySuffix, value, builderExtractor};
  }

  @Test public void propertyTransferredToCollectorBuilder() {
    if (!property.endsWith("url")) {
      TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61616").applyTo(context);
    }

    TestPropertyValues.of("zipkin.collector.activemq.$property:$value").applyTo(context);

    if (property.endsWith("username")) {
      TestPropertyValues.of("zipkin.collector.activemq.password:p").applyTo(context);
    }

    if (property.endsWith("password")) {
      TestPropertyValues.of("zipkin.collector.activemq.username:u").applyTo(context);
    }

    TestPropertyValues.of(property + ":" + value).applyTo(context);
    Access.registerActiveMQProperties(context);
    context.refresh();

    Assertions.assertThat(Access.collectorBuilder(context))
      .extracting(builderExtractor)
      .isEqualTo(value);
  }
}
