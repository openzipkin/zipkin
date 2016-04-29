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
 * This sampler is appropriate for low-traffic instrumentation (ex servers that each receive <100K
 * requests), or those who do not provision random trace ids. It not appropriate for collectors as
 * the sampling decision isn't idempotent (consistent based on trace id).
 *
 * <h3>Implementation</h3>
 *
 * <p>This counts to see how many out of 100 traces should be retained. This means that it is
 * accurate in units of 100 traces.
 */
public final class CountingTraceIdSampler implements TraceIdSampler {

  /**
   * @param rate 0 means never sample, 1 means always sample. Otherwise minimum sample rate is
   * 0.0001, or 0.01% of traces
   */
  public static TraceIdSampler create(final float rate) {
    if (rate == 0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    checkArgument(rate >= 0.01f && rate < 1.0f, "rate should be between 0.01 and 1: was %s", rate);
    return new CountingTraceIdSampler(rate);
  }

  private final int outOf100;

  private int i = 0;
  private boolean skipping = false;

  CountingTraceIdSampler(float rate) {
    this.outOf100 = (int) (rate * 100.0f);
  }

  @Override
  public synchronized boolean isSampled(long traceIdIgnored) {
    boolean result = !skipping;
    i++;
    if (i == outOf100) {
      skipping = true;
    } else if (i == 100) {
      i = 0;
      skipping = false;
    }
    return result;
  }

  @Override
  public String toString() {
    return "CountingTraceIdSampler(" + outOf100 + ")";
  }
}