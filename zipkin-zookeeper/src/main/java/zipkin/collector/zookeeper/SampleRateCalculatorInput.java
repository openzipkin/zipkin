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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes measurements by name and returns a summarized list, or empty. Empty is returned until
 * measurements are notable enough to warrant recalculating a sample rate.
 */
final class SampleRateCalculatorInput
    implements Function<Map<String, Integer>, Optional<List<Integer>>> {
  final Logger log = LoggerFactory.getLogger(SampleRateCalculatorInput.class);

  final AtomicInteger target; // visible for testing
  private final int measurementsInWindow;
  private final int sufficientThreshold;
  private final int requiredOutliers;
  private final ArrayDeque<Map<String, Integer>> buffer;

  SampleRateCalculatorInput(ZooKeeperCollectorSampler.Builder builder, AtomicInteger target) {
    this.target = target;
    this.measurementsInWindow = builder.windowSize / builder.updateFrequency;
    this.sufficientThreshold = builder.sufficientWindowSize / builder.updateFrequency;
    this.requiredOutliers = builder.outlierThreshold / builder.updateFrequency;
    this.buffer = new ArrayDeque<>(measurementsInWindow);
  }

  @Override public Optional<List<Integer>> apply(Map<String, Integer> nextMeasurements) {
    List<Integer> measurements = addMeasurementsAndSumValues(nextMeasurements);
    boolean targetSet = target.get() > 0;
    boolean sufficientMeasurementsInWindow = measurements.size() >= sufficientThreshold;
    boolean allMeasurementsInWindowArePositive = measurements.stream().allMatch(j -> j > 0);
    boolean lastMeasurementsWereOutliers = lastMeasurementsWereOutliers(measurements);
    if (targetSet
        && allMeasurementsInWindowArePositive
        && sufficientMeasurementsInWindow
        && lastMeasurementsWereOutliers) {
      log.debug("summarized measurements in window warrant a new sample rate: {}", measurements);
      return Optional.of(measurements);
    }
    return Optional.empty();
  }

  /**
   * Returns a list of aggregate rate measurements, in insertion order
   */
  private List<Integer> addMeasurementsAndSumValues(Map<String, Integer> nextMeasurements) {
    synchronized (buffer) {
      if (buffer.size() == measurementsInWindow) {
        buffer.remove();
      }
      buffer.add(nextMeasurements);
      log.debug("summarizing measurements in window: {}", buffer);
      return buffer.stream()
          .map(m -> m.values().stream().mapToInt(Integer::intValue).sum())
          .collect(Collectors.toList());
    }
  }

  /** The most recent data has to include only outliers to warrant a sample rate change. */
  private boolean lastMeasurementsWereOutliers(List<Integer> measurements) {
    // outliers have to be the most recent measurements
    int i = Math.max(measurements.size() - requiredOutliers, 0);
    int outliers = 0;
    while (i < measurements.size() && isOutlier(measurements.get(i++))) {
      outliers++;
    }
    return outliers == requiredOutliers;
  }

  /** An outlier is a measurement that is outside 15% of the target */
  private boolean isOutlier(int measurement) {
    int target = this.target.get();
    return Math.abs(measurement - target) > target * 0.15;
  }
}
