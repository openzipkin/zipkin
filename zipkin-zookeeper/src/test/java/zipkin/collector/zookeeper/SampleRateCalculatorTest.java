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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static com.google.common.primitives.Ints.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

public class SampleRateCalculatorTest {

  @Test public void discountedAverage() {
    assertThat(SampleRateCalculator.discountedAverage(asList(10, 5, 0), 1.0f))
        .isEqualTo(5); // normal average

    assertThat(SampleRateCalculator.discountedAverage(asList(10, 5, 0), 0.09f))
        .isEqualTo(9); // smaller discount rate prefers larger numbers

    assertThat(SampleRateCalculator.discountedAverage(asList(1000, 5, 0), 0.09f))
        .isEqualTo(911);
  }

  @Test public void lowersRateToDiscountedAverage() {
    AtomicInteger targetStoreRate = new AtomicInteger(100);
    AtomicReference<Float> sampleRate = new AtomicReference(1.0f);

    SampleRateCalculator calc = new SampleRateCalculator(targetStoreRate, sampleRate);

    assertThat(calc.apply(Optional.of(asList(1000, 1000, 1000))))
        .contains(0.1f); // lowered from 1.0 to 0.1
  }

  /** A collector rebooting shouldn't affect the rate */
  @Test public void discountsSmallMeasurements() {
    AtomicInteger targetStoreRate = new AtomicInteger(100);
    AtomicReference<Float> sampleRate = new AtomicReference(1.0f);

    SampleRateCalculator calc = new SampleRateCalculator(targetStoreRate, sampleRate);

    assertThat(calc.apply(Optional.of(asList(10000, 10000, 150))).get())
        .isCloseTo(0.01f, withPercentage(2));
  }

  /** A surge in traffic should immediately affect the rate */
  @Test public void doesntDiscountBigMeasurements() {
    AtomicInteger targetStoreRate = new AtomicInteger(100);
    AtomicReference<Float> sampleRate = new AtomicReference(1.0f);

    SampleRateCalculator calc = new SampleRateCalculator(targetStoreRate, sampleRate);

    assertThat(calc.apply(Optional.of(asList(10000, 10000, 10000000))).get())
        .isCloseTo(0.0012f, withPercentage(2));
  }

  @Test public void returnsEmptyWhenRateChangeWithin5Percent() {
    AtomicInteger targetStoreRate = new AtomicInteger(100);
    AtomicReference<Float> sampleRate = new AtomicReference(0.1f);

    SampleRateCalculator calc = new SampleRateCalculator(targetStoreRate, sampleRate);

    assertThat(calc.apply(Optional.of(asList(105, 105, 105))))
        .isEmpty();

    assertThat(calc.apply(Optional.of(asList(110, 110, 110))).get())
        .isCloseTo(0.09f, withPercentage(2));
  }
}
