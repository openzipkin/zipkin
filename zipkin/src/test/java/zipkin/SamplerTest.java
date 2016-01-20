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
import java.util.stream.LongStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

public class SamplerTest {
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
    Sampler sampler = Sampler.create(0.1f);

    assertThat(sampler.isSampled(Long.MIN_VALUE))
        .isEqualTo(sampler.isSampled(Long.MAX_VALUE));
  }

  @Test
  public void retain10Percent() {
    float sampleRate = 0.1f;
    Sampler sampler = Sampler.create(sampleRate);

    long passCount = LongStream.of(traceIds).filter(sampler::isSampled).count();

    assertThat(passCount)
        .isCloseTo((long) (traceIds.length * sampleRate), withPercentage(3));
  }

  /**
   * The collector needs to apply the same decision to incremental updates in a trace.
   */
  @Test
  public void idempotent() {
    Sampler sampler1 = Sampler.create(0.1f);
    Sampler sampler2 = Sampler.create(0.1f);

    assertThat(LongStream.of(traceIds).filter(sampler1::isSampled).toArray())
        .containsExactly(LongStream.of(traceIds).filter(sampler2::isSampled).toArray());
  }

  @Test
  public void zeroMeansDropAllTraces() {
    Sampler sampler = Sampler.create(0.0f);
    assertThat(sampler).isSameAs(Sampler.NEVER_SAMPLE);

    assertThat(LongStream.of(traceIds).filter(sampler::isSampled).findAny())
        .isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() {
    Sampler sampler = Sampler.create(1.0f);
    assertThat(sampler).isSameAs(Sampler.ALWAYS_SAMPLE);

    assertThat(LongStream.of(traceIds).filter(sampler::isSampled).toArray())
        .containsExactly(traceIds);
  }

  @Test
  public void rateCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was -1.0");

    Sampler.create(-1.0f);
  }

  @Test
  public void rateCantBeOverOne() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was 1.1");

    Sampler.create(1.1f);
  }
}
