/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.rabbitmq.client.ConnectionFactory;
import java.io.UncheckedIOException;
import org.junit.Before;
import org.junit.Test;
import zipkin2.CheckResult;
import zipkin2.Component;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RabbitMQCollectorTest {

  RabbitMQCollector collector;

  @Before public void before() {
    ConnectionFactory connectionFactory = new ConnectionFactory();
    connectionFactory.setConnectionTimeout(100);
    // We can be pretty certain RabbitMQ isn't running on localhost port 80
    collector = RabbitMQCollector.builder()
        .connectionFactory(connectionFactory).addresses(asList("localhost:80")).build();
  }

  @Test public void checkFalseWhenRabbitMQIsDown() {
    CheckResult check = collector.check();
    assertThat(check.ok()).isFalse();
    assertThat(check.error()).isInstanceOf(UncheckedIOException.class);
  }

  @Test public void startFailsWhenRabbitMQIsDown() {
    // NOTE.. This is probably not good as it can crash on transient failure..
    assertThatThrownBy(collector::start)
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageStartingWith("Unable to establish connection to RabbitMQ server");
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(collector).hasToString(
        "RabbitMQCollector{addresses=[localhost:80], queue=zipkin}"
    );
  }
}
