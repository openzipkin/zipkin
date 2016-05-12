/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server.brave;

import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.Codec;
import zipkin.InMemoryCollectorMetrics;
import zipkin.StorageComponent;
import zipkin.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static zipkin.collector.CollectorMetrics.NOOP_METRICS;
import static zipkin.collector.CollectorSampler.ALWAYS_SAMPLE;

public class LocalSpanCollectorTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Mock
  StorageComponent storage;
  InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  InMemoryCollectorMetrics localMetrics = metrics.forTransport("local");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void flushIntervalCantBeZero() {
    thrown.expect(IllegalArgumentException.class);

    new LocalSpanCollector(storage, 0, ALWAYS_SAMPLE, NOOP_METRICS);
  }

  @Test
  public void flushIntervalCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);

    new LocalSpanCollector(storage, -1, ALWAYS_SAMPLE, NOOP_METRICS);
  }

  @Test
  public void sendSpans_incrementsMetricsOnSuccess() throws IOException {
    when(storage.asyncSpanConsumer())
        .thenReturn((spans, callback) -> callback.onSuccess(null));

    byte[] bytes = Codec.THRIFT.writeSpans(TestObjects.TRACE);
    try (LocalSpanCollector c = new LocalSpanCollector(storage, 1, ALWAYS_SAMPLE, metrics)) {
      c.sendSpans(bytes);
    }

    assertThat(localMetrics.messages()).isEqualTo(1);
    assertThat(localMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(localMetrics.spans()).isEqualTo(TestObjects.TRACE.size());
  }

  @Test
  public void sendSpans_incrementsMetricsOnQueuingError() throws IOException {
    when(storage.asyncSpanConsumer())
        .thenReturn((spans, callback) -> {
          throw new RuntimeException();
        });

    byte[] bytes = Codec.THRIFT.writeSpans(TestObjects.TRACE);
    try (LocalSpanCollector c = new LocalSpanCollector(storage, 1, ALWAYS_SAMPLE, metrics)) {
      c.sendSpans(bytes);
    }

    assertThat(localMetrics.spansDropped()).isEqualTo(TestObjects.TRACE.size());
  }

  @Test
  public void sendSpans_incrementsMetricsOnCallbackError() throws IOException {
    when(storage.asyncSpanConsumer())
        .thenReturn((spans, callback) -> callback.onError(new RuntimeException()));

    byte[] bytes = Codec.THRIFT.writeSpans(TestObjects.TRACE);
    try (LocalSpanCollector c = new LocalSpanCollector(storage, 1, ALWAYS_SAMPLE, metrics)) {
      c.sendSpans(bytes);
    }

    assertThat(localMetrics.spansDropped()).isEqualTo(TestObjects.TRACE.size());
  }
}
