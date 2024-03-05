/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
