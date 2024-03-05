/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

class CollectorSamplerTest {

  /**
   * Math.abs("8000000000000000") returns a negative, we coerse to "7fffffffffffffff" to avoid
   * always dropping when trace_id == "8000000000000000"
   */
  @Test void mostNegativeNumberDefence() {
    CollectorSampler sampler = CollectorSampler.create(0.1f);

    assertThat(sampler.isSampled("8000000000000000", false))
        .isEqualTo(sampler.isSampled("7fffffffffffffff", false));
  }

  @Test void debugWins() {
    CollectorSampler sampler = CollectorSampler.create(0.0f);

    assertThat(sampler.isSampled("8000000000000000", true)).isTrue();
  }

  @Test void retain10Percent() {
    float sampleRate = 0.1f;
    CollectorSampler sampler = CollectorSampler.create(sampleRate);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId(), false)).count())
        .isCloseTo((long) (LOTS_OF_SPANS.length * sampleRate), withPercentage(3));
  }

  /**
   * The collector needs to apply the same decision to incremental updates in a trace.
   */
  @Test void idempotent() {
    CollectorSampler sampler1 = CollectorSampler.create(0.1f);
    CollectorSampler sampler2 = CollectorSampler.create(0.1f);

    assertThat(lotsOfSpans().filter(s -> sampler1.isSampled(s.traceId(), false)).toArray())
        .containsExactly(
            lotsOfSpans().filter(s -> sampler2.isSampled(s.traceId(), false)).toArray());
  }

  @Test void zeroMeansDropAllTraces() {
    CollectorSampler sampler = CollectorSampler.create(0.0f);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId(), false))).isEmpty();
  }

  @Test void oneMeansKeepAllTraces() {
    CollectorSampler sampler = CollectorSampler.create(1.0f);

    assertThat(lotsOfSpans().filter(s -> sampler.isSampled(s.traceId(), false)))
        .hasSize(LOTS_OF_SPANS.length);
  }

  @Test void rateCantBeNegative() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> CollectorSampler.create(-1.0f));
    assertThat(exception.getMessage()).contains("rate should be between 0 and 1: was -1.0");
  }

  @Test void rateCantBeOverOne() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> CollectorSampler.create(1.1f));
    assertThat(exception.getMessage()).contains("rate should be between 0 and 1: was 1.1");
  }

  static Stream<Span> lotsOfSpans() {
    return Stream.of(LOTS_OF_SPANS).parallel();
  }
}
