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

import java.util.Random;
import java.util.stream.LongStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

public class TraceIdSamplerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  long[] traceIds = new Random().longs(100000).toArray();

  /**
   * Math.abs(Long.MIN_VALUE) returns a negative, we coerse to Long.MAX_VALUE to avoid always
   * dropping when trace_id == Long.MIN_VALUE
   */
  @Test
  public void mostNegativeNumberDefence() {
    TraceIdSampler sampler = TraceIdSampler.create(0.1f);

    assertThat(sampler.test(Long.MIN_VALUE))
        .isEqualTo(sampler.test(Long.MAX_VALUE));
  }

  @Test
  public void retain10Percent() {
    float sampleRate = 0.1f;
    TraceIdSampler sampler = TraceIdSampler.create(sampleRate);

    long passCount = LongStream.of(traceIds).filter(sampler::test).count();

    assertThat(passCount)
        .isCloseTo((long) (traceIds.length * sampleRate), withPercentage(3));
  }

  /**
   * The collector needs to apply the same decision to incremental updates in a trace.
   */
  @Test
  public void idempotent() {
    TraceIdSampler sampler1 = TraceIdSampler.create(0.1f);
    TraceIdSampler sampler2 = TraceIdSampler.create(0.1f);

    assertThat(LongStream.of(traceIds).filter(sampler1::test).toArray())
        .containsExactly(LongStream.of(traceIds).filter(sampler2::test).toArray());
  }

  @Test
  public void zeroMeansDropAllTraces() {
    TraceIdSampler sampler = TraceIdSampler.create(0.0f);
    assertThat(sampler).isSameAs(TraceIdSampler.NEVER_SAMPLE);

    assertThat(LongStream.of(traceIds).filter(sampler::test).findAny())
        .isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() {
    TraceIdSampler sampler = TraceIdSampler.create(1.0f);
    assertThat(sampler).isSameAs(TraceIdSampler.ALWAYS_SAMPLE);

    assertThat(LongStream.of(traceIds).filter(sampler::test).toArray())
        .containsExactly(traceIds);
  }

  @Test
  public void rateCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was -1.0");

    TraceIdSampler.create(-1.0f);
  }

  @Test
  public void rateCantBeOverOne() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was 1.1");

    TraceIdSampler.create(1.1f);
  }
}
