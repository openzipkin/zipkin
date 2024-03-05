/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.throttle;

import brave.Tracing;
import com.linecorp.armeria.common.metric.NoopMeterRegistry;
import org.junit.jupiter.api.Test;
import zipkin2.Component;
import zipkin2.internal.Nullable;
import zipkin2.server.internal.throttle.ThrottledStorageComponent.ThrottledSpanConsumer;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ThrottledStorageComponentTest {
  InMemoryStorage delegate = InMemoryStorage.newBuilder().build();
  @Nullable Tracing tracing;
  NoopMeterRegistry registry = NoopMeterRegistry.get();

  @Test void spanConsumer_isProxied() {
    ThrottledStorageComponent throttle =
      new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, 1);

    assertThat(ThrottledSpanConsumer.class)
      .isSameAs(throttle.spanConsumer().getClass());
  }

  @Test void createComponent_withZeroSizedQueue() {
    int queueSize = 0;
    new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, queueSize);
    // no exception == pass
  }

  @Test void createComponent_withNegativeQueue() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      int queueSize = -1;
      new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, queueSize);
    });
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    assertThat(new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, 1))
      .hasToString("Throttled{InMemoryStorage{}}");
  }

  @Test void delegatesCheck() {
    StorageComponent mock = mock(StorageComponent.class);

    new ThrottledStorageComponent(mock, registry, tracing, 1, 2, 1).check();
    verify(mock, times(1)).check();
  }
}
