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
package io.zipkin;

import static io.zipkin.internal.Util.checkArgument;

/**
 * Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the trace
 * is made before any work is measured, or annotations are added. As such, the input parameter to
 * zipkin v1 samplers is the trace id (64-bit random number).
 *
 * <p>The implementation is based on zipkin-scala's AdjustableGlobalSampler. It is percentage based,
 * and its accuracy is tied to distribution of traceIds across 64bits. For example, tests have shown
 * an error rate of 3% when traceIds are random. It is idempotent, ie a consistent decision for a
 * given trace id.
 */
// abstract for factory-method support on Java language level 7
public abstract class TraceIdSampler {

  /**
   * Returns true if the traceId should be retained.
   *
   * <p>This implementation compares the absolute value of the trace id against a product of the
   * sample rate. It defends against the most negative number in two's complement.
   */
  public abstract boolean test(long traceId);

  /**
   * Returns a constant sampler, given a rate expressed as a percentage.
   *
   * @param rate minimum sample rate is 0.0001, or 0.01% of traces
   */
  public static TraceIdSampler create(float rate) {
    if (rate == 0.0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    return new ThresholdSampler(rate);
  }

  static final TraceIdSampler ALWAYS_SAMPLE = new TraceIdSampler() {
    @Override
    public boolean test(long traceId) {
      return true;
    }

    @Override
    public String toString() {
      return "ALWAYS_SAMPLE";
    }
  };

  static final TraceIdSampler NEVER_SAMPLE = new TraceIdSampler() {
    @Override
    public boolean test(long traceId) {
      return false;
    }

    @Override
    public String toString() {
      return "NEVER_SAMPLE";
    }
  };

  /**
   * Given the absolute value of a random 64 bit trace id, we expect inputs to be balanced across
   * 0-MAX. Threshold is the range of inputs between 0-MAX that we retain.
   */
  static final class ThresholdSampler extends TraceIdSampler {

    private final long threshold;

    ThresholdSampler(float rate) {
      checkArgument(rate > 0 && rate < 1, "rate should be between 0 and 1: was %s", rate);
      this.threshold = (long) (Long.MAX_VALUE * rate); // safe cast as rate is less than 1
    }

    /**
     * Returns true if the traceId should be retained.
     *
     * <p>This implementation compares the absolute value of the trace id against a product of the
     * sample rate. It defends against the most negative number in two's complement.
     */
    @Override
    public boolean test(long traceId) {
      // The absolute value of Long.MIN_VALUE is larger than a long, so returns Math.abs identity.
      // This converts to MAX_VALUE to avoid always dropping when traceId == Long.MIN_VALUE
      long t = traceId == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(traceId);
      return t < threshold;
    }

    @Override
    public String toString() {
      return "ThresholdSampler(" + threshold + ")";
    }
  }
}
