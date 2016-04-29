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

import java.util.BitSet;
import java.util.Random;

import static zipkin.internal.Util.checkArgument;

/**
 * This sampler is appropriate for low-traffic instrumentation (ex servers that each receive <100K
 * requests), or those who do not provision random trace ids. It not appropriate for collectors as
 * the sampling decision isn't idempotent (consistent based on trace id).
 *
 * <h3>Implementation</h3>
 *
 * <p>This initializes a random bitset of size 100 (corresponding to 1% granularity). This means
 * that it is accurate in units of 100 traces. At runtime, this loops through the bitset, returning
 * the value according to a counter.
 */
public final class CountingTraceIdSampler implements TraceIdSampler {

  /**
   * @param rate 0 means never sample, 1 means always sample. Otherwise minimum sample rate is 0.01,
   * or 1% of traces
   */
  public static TraceIdSampler create(final float rate) {
    if (rate == 0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    checkArgument(rate >= 0.01f && rate < 1, "rate should be between 0.01 and 1: was %s", rate);
    return new CountingTraceIdSampler(rate);
  }

  private int i; // guarded by this
  private final BitSet sampleDecisions;

  /** Fills a bitset with decisions according to the supplied rate. */
  CountingTraceIdSampler(float rate) {
    int outOf100 = (int) (rate * 100.0f);
    this.sampleDecisions = randomBitSet(100, outOf100, new Random());
  }

  /** loops over the pre-canned decisions, resetting to zero when it gets to the end. */
  @Override
  public synchronized boolean isSampled(long traceIdIgnored) {
    boolean result = sampleDecisions.get(i++);
    if (i == 100) i = 0;
    return result;
  }

  @Override
  public String toString() {
    return "CountingTraceIdSampler()";
  }

  /**
   * Reservoir sampling algorithm borrowed from Stack Overflow.
   *
   * http://stackoverflow.com/questions/12817946/generate-a-random-bitset-with-n-1s
   */
  static BitSet randomBitSet(int size, int cardinality, Random rnd) {
    BitSet result = new BitSet(size);
    int[] chosen = new int[cardinality];
    int i;
    for (i = 0; i < cardinality; ++i) {
      chosen[i] = i;
      result.set(i);
    }
    for (; i < size; ++i) {
      int j = rnd.nextInt(i + 1);
      if (j < cardinality) {
        result.clear(chosen[j]);
        result.set(i);
        chosen[j] = i;
      }
    }
    return result;
  }
}
