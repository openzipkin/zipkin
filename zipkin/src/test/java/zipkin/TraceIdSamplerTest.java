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

import java.util.stream.Stream;
import org.assertj.core.data.Percentage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;

@RunWith(Theories.class)
public abstract class TraceIdSamplerTest {
  abstract TraceIdSampler newSampler(float rate);

  abstract Percentage expectedErrorRate();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @DataPoints
  public static final float[] SAMPLE_RATES = {0.01f, 0.5f, 0.9f};

  @Theory
  public void retainsPerSampleRate(float sampleRate) {
    TraceIdSampler sampler = newSampler(sampleRate);

    // parallel to ensure there aren't any unsynchronized race conditions
    long passed = traceIds().parallel().filter(sampler::isSampled).count();

    assertThat(passed)
        .isCloseTo((long) (LOTS_OF_SPANS.length * sampleRate), expectedErrorRate());
  }

  @Test
  public void zeroMeansDropAllTraces() {
    TraceIdSampler sampler = newSampler(0.0f);

    assertThat(traceIds().filter(sampler::isSampled).findAny())
        .isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() {
    TraceIdSampler sampler = newSampler(1.0f);

    assertThat(traceIds().filter(sampler::isSampled))
        .hasSize(LOTS_OF_SPANS.length);
  }

  @Test
  public void rateCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);

    newSampler(-1.0f);
  }

  @Test
  public void rateCantBeOverOne() {
    thrown.expect(IllegalArgumentException.class);

    newSampler(1.1f);
  }

  Stream<Long> traceIds() {
    return Stream.of(LOTS_OF_SPANS).map(s -> s.traceId);
  }
}
