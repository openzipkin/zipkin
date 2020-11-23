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
package zipkin2.collector.scribe;

import org.junit.jupiter.api.Test;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScribeCollectorTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();

  @Test void check_failsWhenNotStarted() {
    try (ScribeCollector scribe = ScribeCollector.newBuilder().storage(storage).port(0).build()) {

      CheckResult result = scribe.check();
      assertThat(result.ok()).isFalse();
      assertThat(result.error()).isInstanceOf(IllegalStateException.class);

      scribe.start();
      assertThat(scribe.check().ok()).isTrue();
    }
  }

  @Test void anonymousPort() {
    try (ScribeCollector scribe = ScribeCollector.newBuilder().storage(storage).port(0).build()) {

      assertThat(scribe.port()).isZero();

      scribe.start();
      assertThat(scribe.port()).isNotZero();
    }
  }

  @Test void start_failsWhenCantBindPort() {
    ScribeCollector.Builder builder = ScribeCollector.newBuilder().storage(storage).port(0);

    try (ScribeCollector first = builder.build().start()) {
      try (ScribeCollector samePort = builder.port(first.port()).build()) {
        assertThatThrownBy(samePort::start)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Could not start scribe server.");
      }
    }
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    try (ScribeCollector scribe = ScribeCollector.newBuilder().storage(storage).port(0).build()) {

      assertThat(scribe.start())
        .hasToString("ScribeCollector{port=" + scribe.port() + ", category=zipkin}");
    }
  }
}
