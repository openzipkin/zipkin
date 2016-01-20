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
package zipkin;

import static zipkin.internal.Util.checkArgument;

/**
 * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. recorded in
 * permanent storage.
 *
 * <p>Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the
 * trace is made before any work is measured, or annotations are added. As such, the input parameter
 * to zipkin v1 samplers is the trace ID (64-bit random number).
 */
// abstract for factory-method support on Java language level 7
public abstract class Sampler {

  /** Returns true if the trace ID should be recorded. */
  public abstract boolean isSampled(long traceId);

  /**
   * Returns a sampler, given a rate expressed as a percentage.
   *
   * @param rate minimum sample rate is 0.0001, or 0.01% of traces
   * @see ProbabilisticSampler
   */
  public static Sampler create(float rate) {
    if (rate == 0.0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    return new ProbabilisticSampler(rate);
  }

  static final Sampler ALWAYS_SAMPLE = new Sampler() {
    @Override
    public boolean isSampled(long traceId) {
      return true;
    }

    @Override
    public String toString() {
      return "ALWAYS_SAMPLE";
    }
  };

  static final Sampler NEVER_SAMPLE = new Sampler() {
    @Override
    public boolean isSampled(long traceId) {
      return false;
    }

    @Override
    public String toString() {
      return "NEVER_SAMPLE";
    }
  };

  /**
   * Accepts a percentage of trace ids by comparing their absolute value against a boundary. eg
   * {@code iSampled == abs(traceId) < boundary}
   *
   * <p>While idempotent, this implementation's sample rate won't exactly match the input rate
   * because trace ids are not perfectly distributed across 64bits. For example, tests have shown an
   * error rate of 3% when trace ids are {@link java.util.Random#nextLong random}.
   */
  public static final class ProbabilisticSampler extends Sampler {

    /** {@link #isSampled(long)} returns true when abs(traceId) < boundary */
    private final long boundary;

    public ProbabilisticSampler(float rate) {
      checkArgument(rate > 0 && rate < 1, "rate should be between 0 and 1: was %s", rate);
      this.boundary = (long) (Long.MAX_VALUE * rate); // safe cast as less than 1
    }

    @Override
    public boolean isSampled(long traceId) {
      // The absolute value of Long.MIN_VALUE is larger than a long, so Math.abs returns identity.
      // This converts to MAX_VALUE to avoid always dropping when traceId == Long.MIN_VALUE
      long t = traceId == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(traceId);
      return t < boundary;
    }

    @Override
    public String toString() {
      return "ProbabilisticSampler(" + boundary + ")";
    }
  }
}
