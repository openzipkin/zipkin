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

/**
 * TraceIdSampler decides if a particular trace ID should be "sampled".
 *
 * <p>For instrumentation, this decides whether the overhead of tracing will occur and/or if a trace
 * will be reported to the collection tier. The instrumentation sampling decision happens once, at
 * the root of the trace, and is propagated downstream. For this reason, the decision needn't be
 * consistent based on trace ID.
 *
 * <p>For collectors, this is a major input to the decision of whether a trace will be written to
 * storage. Unlike instrumentation, collector's tracing decision needs to be consistent across
 * nodes. For this reason, the implementation must be consistent on trace ID.
 */
public interface TraceIdSampler {
  TraceIdSampler ALWAYS_SAMPLE = new TraceIdSampler() {
    @Override public boolean isSampled(long traceId) {
      return true;
    }

    @Override public String toString() {
      return "AlwaysSample";
    }
  };

  TraceIdSampler NEVER_SAMPLE = new TraceIdSampler() {
    @Override public boolean isSampled(long traceId) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /**
   * Returns true if a trace should be measured.
   *
   * <p>Zipkin v1 instrumentation uses before-the-fact sampling. This means that the decision to
   * keep or drop the trace is made before any work is measured, or annotations are added. As such,
   * the input parameter to zipkin v1 samplers is the trace ID (64-bit random number).
   */
  boolean isSampled(long traceId);
}
