/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.collector;

import zipkin.Span;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkArgument;

/**
 * CollectorSampler decides if a particular trace should be "sampled", i.e. recorded in permanent
 * storage. This involves a consistent decision based on the span's trace ID with one notable
 * exception: {@link Span#debug Debug} spans are always stored.
 *
 * <h3>Implementation</h3>
 *
 * <p>Accepts a percentage of trace ids by comparing their absolute value against a potentially
 * dynamic boundary. eg {@code isSampled == abs(traceId) <= boundary}
 *
 * <p>While idempotent, this implementation's sample rate won't exactly match the input rate because
 * trace ids are not perfectly distributed across 64bits. For example, tests have shown an error
 * rate of 3% when 100K trace ids are {@link java.util.Random#nextLong random}.
 */
public abstract class CollectorSampler {
  public static final CollectorSampler ALWAYS_SAMPLE = CollectorSampler.create(1.0f);

  /**
   * @param rate minimum sample rate is 0.0001, or 0.01% of traces
   */
  public static CollectorSampler create(float rate) {
    checkArgument(rate >= 0 && rate <= 1, "rate should be between 0 and 1: was %s", rate);
    final long boundary = (long) (Long.MAX_VALUE * rate); // safe cast as less <= 1
    return new CollectorSampler() {
      @Override protected long boundary() {
        return boundary;
      }
    };
  }

  protected abstract long boundary();

  /**
   * Returns true if spans with this trace ID should be recorded to storage.
   *
   * <p>Zipkin v1 allows storage-layer sampling, which can help prevent spikes in traffic from
   * overloading the system. Debug spans are always stored.
   *
   * <p>This uses only the lower 64 bits of the trace ID as instrumentation still send mixed trace
   * ID width.
   *
   * @param traceId the lower 64 bits of the span's trace ID
   * @param debug when true, always passes sampling
   */
  public boolean isSampled(long traceId, @Nullable Boolean debug) {
    if (Boolean.TRUE.equals(debug)) return true;

    // The absolute value of Long.MIN_VALUE is larger than a long, so Math.abs returns identity.
    // This converts to MAX_VALUE to avoid always dropping when traceId == Long.MIN_VALUE
    long t = traceId == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(traceId);
    return t <= boundary();
  }

  /** @deprecated use {@link #isSampled(long, Boolean)} as that works with multiple span formats */
  @Deprecated public boolean isSampled(Span span) {
    return isSampled(span.traceId, span.debug);
  }

  @Override
  public String toString() {
    return "CollectorSampler(" + boundary() + ")";
  }

  protected CollectorSampler() {
  }
}
