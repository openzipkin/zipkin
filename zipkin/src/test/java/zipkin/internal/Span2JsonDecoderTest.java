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
package zipkin.internal;

import org.junit.Test;
import zipkin.Span;
import zipkin.SpanDecoder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;

public class Span2JsonDecoderTest {
  Span span1 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]);
  Span span2 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1]);
  Span2 span2_1 = Span2Converter.fromSpan(span1).get(0);
  Span2 span2_2 = Span2Converter.fromSpan(span2).get(0);

  SpanDecoder decoder = new Span2JsonDecoder();

  @Test public void readSpan() {
    assertThat(decoder.readSpan(Span2Codec.JSON.writeSpan(span2_1)))
      .isEqualTo(span1);
  }

  @Test public void readSpans() {
    assertThat(decoder.readSpans(Span2Codec.JSON.writeSpans(asList(span2_1, span2_2))))
      .containsExactly(span1, span2);
  }
}
