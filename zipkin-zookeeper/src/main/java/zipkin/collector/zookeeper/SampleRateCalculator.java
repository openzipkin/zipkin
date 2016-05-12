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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This calculates a new sample rate given the input measurements or returns empty if the current
 * rate is fine.
 *
 * <p>The sample rate is calculated using a discounted average. The discount rate is low (0.09),
 * which prefers larger numbers. This is used to ensure large measurements (like a traffic spike)
 * trip a lower sample rate, while small measurements (like a new collector instance) don't.
 */
final class SampleRateCalculator implements Function<Optional<List<Integer>>, Optional<Float>> {
  final Logger log = LoggerFactory.getLogger(SampleRateCalculator.class);

  private final AtomicInteger target;
  private final AtomicReference<Float> sampleRate;
  private final float discountRate = 0.09f;
  private final float threshold = 0.05f;

  SampleRateCalculator(AtomicInteger target, AtomicReference<Float> sampleRate) {
    this.target = target;
    this.sampleRate = sampleRate;
  }

  @Override public Optional<Float> apply(Optional<List<Integer>> input) {
    if (!input.isPresent()) return Optional.empty();
    List<Integer> measurements = input.get();
    int discountedAverage = discountedAverage(measurements, discountRate);
    log.debug("{} discounted average measurement: {}", discountRate, discountedAverage);
    if (discountedAverage <= 0) return Optional.empty();

    float oldSampleRate = sampleRate.get();
    float newSampleRate = Math.min(1.0f, oldSampleRate * target.get() / discountedAverage);

    float change = Math.abs(oldSampleRate - newSampleRate) / oldSampleRate;
    if (change > 0.0f) {
      log.debug("Sample rate changed {} from {} to {}", change, oldSampleRate, sampleRate);
      if (change < threshold) {
        log.debug("Sample rate change was less than {} threshold. Ignoring", threshold);
      }
    }
    return change >= threshold ? Optional.of(newSampleRate) : Optional.empty();
  }

  static int discountedAverage(List<Integer> measurements, float discountRate) {
    double discountTotal = 0;
    double discountedVals = 0;
    for (int i = 0; i < measurements.size(); i++) {
      double discount = Math.pow(discountRate, i);
      discountTotal += discount;
      discountedVals += discount * measurements.get(i);
    }
    return (int) (discountedVals / discountTotal);
  }
}
