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
package zipkin.collector.zookeeper;

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static com.google.common.primitives.Ints.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class SampleRateCalculatorInputTest {

  @Test public void presentWhenTargetIsPositive() {
    SampleRateCalculatorInput rates = new SampleRateCalculatorInput(new ZooKeeperCollectorSampler.Builder()
        .windowSize(1)
        .updateFrequency(1)
        .sufficientWindowSize(1)
        .outlierThreshold(1), new AtomicInteger(1));

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 10)))
        .isPresent();

    rates.target.set(0);

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 10)))
        .isEmpty();
  }

  @Test public void emptyWhenInsufficientData() {
    SampleRateCalculatorInput rates = new SampleRateCalculatorInput(new ZooKeeperCollectorSampler.Builder()
        .windowSize(3)
        .sufficientWindowSize(2)
        .updateFrequency(1)
        .outlierThreshold(1), new AtomicInteger(1));

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 10)))
        .isEmpty();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 20)))
        .isPresent();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 30)))
        .isPresent();
  }

  @Test public void emptyWhenElementIsNotPositive() {
    SampleRateCalculatorInput rates = new SampleRateCalculatorInput(new ZooKeeperCollectorSampler.Builder()
        .windowSize(1)
        .updateFrequency(1)
        .sufficientWindowSize(1)
        .outlierThreshold(1), new AtomicInteger(1));

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 10)))
        .contains(asList(10));

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host2:8080", 0)))
        .isEmpty();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 30)))
        .isPresent();
  }

  @Test public void emptyUntilEnoughOutliers() {
    SampleRateCalculatorInput rates = new SampleRateCalculatorInput(new ZooKeeperCollectorSampler.Builder()
        .windowSize(3)
        .sufficientWindowSize(1)
        .updateFrequency(1)
        .outlierThreshold(2), new AtomicInteger(20)); // <17 or >23 is an outlier

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 25)))
        .isEmpty();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 20))) // not outlier
        .isEmpty();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 25)))
        .isEmpty();

    assertThat(rates.apply(ImmutableMap.of("zipkin-server@host:8080", 25)))
        .isPresent();
  }
}
