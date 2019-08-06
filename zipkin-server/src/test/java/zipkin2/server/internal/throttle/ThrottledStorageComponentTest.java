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
package zipkin2.server.internal.throttle;

import brave.Tracing;
import com.linecorp.armeria.common.metric.NoopMeterRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Component;
import zipkin2.internal.Nullable;
import zipkin2.server.internal.throttle.ThrottledStorageComponent.ThrottledSpanConsumer;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ThrottledStorageComponentTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();
  InMemoryStorage delegate = InMemoryStorage.newBuilder().build();
  @Nullable Tracing tracing;
  NoopMeterRegistry registry = NoopMeterRegistry.get();

  @Test public void spanConsumer_isProxied() {
    ThrottledStorageComponent throttle =
      new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, 1);

    assertThat(ThrottledSpanConsumer.class)
      .isSameAs(throttle.spanConsumer().getClass());
  }

  @Test public void createComponent_withZeroSizedQueue() {
    int queueSize = 0;
    new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, queueSize);
    // no exception == pass
  }

  @Test public void createComponent_withNegativeQueue() {
    expectedException.expect(IllegalArgumentException.class);
    int queueSize = -1;
    new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, queueSize);
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(new ThrottledStorageComponent(delegate, registry, tracing, 1, 2, 1))
      .hasToString("Throttled{InMemoryStorage{}}");
  }

  @Test public void delegatesCheck() {
    StorageComponent mock = mock(StorageComponent.class);

    new ThrottledStorageComponent(mock, registry, tracing, 1, 2, 1).check();
    verify(mock, times(1)).check();
  }
}
