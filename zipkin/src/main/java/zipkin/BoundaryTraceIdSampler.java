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

import java.util.Random;

import static zipkin.internal.Util.checkArgument;

/**
 * This sampler is appropriate for high-traffic instrumentation (ex edge web servers that each
 * receive >100K requests) who provision random trace ids, and make the sampling decision only once.
 * It defends against nodes in the cluster selecting exactly the same ids.
 *
 * <h3>Implementation</h3>
 *
 * <p>This uses modulo 10000 arithmetic, which allows a minimum sample rate of 0.01%. Trace id
 * collision was noticed in practice in the Twitter front-end cluster. A random salt is here to
 * defend against nodes in the same cluster sampling exactly the same subset of trace ids. The goal
 * was full 64-bit coverage of trace IDs on multi-host deployments.
 *
 * <p>Based on https://github.com/twitter/finagle/blob/develop/finagle-zipkin/src/main/scala/com/twitter/finagle/zipkin/thrift/Sampler.scala#L68
 */
public final class BoundaryTraceIdSampler implements TraceIdSampler {
  static final long SALT = new Random().nextLong();

  /**
   * @param rate 0 means never sample, 1 means always sample. Otherwise minimum sample rate is
   * 0.0001, or 0.01% of traces
   */
  public static TraceIdSampler create(float rate) {
    if (rate == 0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    checkArgument(rate > 0.0001 && rate < 1, "rate should be between 0.0001 and 1: was %s", rate);
    final long boundary = (long) (rate * 10000); // safe cast as less <= 1
    return new BoundaryTraceIdSampler(boundary);
  }

  private final long boundary;

  BoundaryTraceIdSampler(long boundary) {
    this.boundary = boundary;
  }

  /** Returns true when {@code abs(traceId) <= boundary} */
  @Override
  public boolean isSampled(long traceId) {
    long t = Math.abs(traceId ^ SALT);
    return t % 10000 <= boundary; // Constant expression for readability
  }

  @Override
  public String toString() {
    return "BoundaryTraceIdSampler(" + boundary + ")";
  }
}