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

import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static zipkin.TestObjects.LOTS_OF_SPANS;

public class CollectorSamplerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * Math.abs(Long.MIN_VALUE) returns a negative, we coerse to Long.MAX_VALUE to avoid always
   * dropping when trace_id == Long.MIN_VALUE
   */
  @Test
  public void mostNegativeNumberDefence() {
    CollectorSampler sampler = CollectorSampler.create(0.1f);

    assertThat(sampler.isSampled(Long.MIN_VALUE, null))
      .isEqualTo(sampler.isSampled(Long.MAX_VALUE, null));
  }

  @Test
  public void debugWins() {
    CollectorSampler sampler = CollectorSampler.create(0.0f);

    assertThat(sampler.isSampled(Long.MIN_VALUE, true))
      .isTrue();
  }

  @Test
  public void retain10Percent() {
    float sampleRate = 0.1f;
    CollectorSampler sampler = CollectorSampler.create(sampleRate);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId, null)).count())
      .isCloseTo((long) (LOTS_OF_SPANS.length * sampleRate), withPercentage(3));
  }

  /**
   * The collector needs to apply the same decision to incremental updates in a trace.
   */
  @Test
  public void idempotent() {
    CollectorSampler sampler1 = CollectorSampler.create(0.1f);
    CollectorSampler sampler2 = CollectorSampler.create(0.1f);

    assertThat(lotsOfSpans().filter(s -> sampler1.isSampled(s.traceId, null)).toArray())
      .containsExactly(lotsOfSpans().filter(s -> sampler2.isSampled(s.traceId, null)).toArray());
  }

  @Test
  public void zeroMeansDropAllTraces() {
    CollectorSampler sampler = CollectorSampler.create(0.0f);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId, null)))
      .isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() {
    CollectorSampler sampler = CollectorSampler.create(1.0f);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId, null)))
      .hasSize(LOTS_OF_SPANS.length);
  }

  @Test
  public void rateCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was -1.0");

    CollectorSampler.create(-1.0f);
  }

  @Test
  public void rateCantBeOverOne() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("rate should be between 0 and 1: was 1.1");

    CollectorSampler.create(1.1f);
  }

  static Stream<Span> lotsOfSpans() {
    return Stream.of(LOTS_OF_SPANS).parallel();
  }
}
