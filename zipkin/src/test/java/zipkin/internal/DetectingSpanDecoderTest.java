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
import zipkin.Codec;
import zipkin.SpanDecoder;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;

public class DetectingSpanDecoderTest {
  zipkin.Span span1 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]);
  zipkin.Span span2 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1]);
  Span span2_1 = V2SpanConverter.fromSpan(span1).get(0);
  Span span2_2 = V2SpanConverter.fromSpan(span2).get(0);

  SpanDecoder decoder = new DetectingSpanDecoder();

  @Test public void readSpan_json() {
    assertThat(decoder.readSpan(Codec.JSON.writeSpan(span1)))
      .isEqualTo(span1);
  }

  @Test(expected = IllegalArgumentException.class) public void readSpan_json_list() {
    decoder.readSpan(Codec.JSON.writeSpans(asList(span1, span2)));
  }

  @Test public void readSpans_json() {
    assertThat(decoder.readSpans(Codec.JSON.writeSpans(asList(span1, span2))))
      .containsExactly(span1, span2);
  }

  @Test(expected = IllegalArgumentException.class) public void readSpans_json_not_list() {
    decoder.readSpans(Codec.JSON.writeSpan(span1));
  }

  @Test public void readSpan_thrift() {
    assertThat(decoder.readSpan(Codec.THRIFT.writeSpan(span1)))
      .isEqualTo(span1);
  }

  @Test(expected = IllegalArgumentException.class) public void readSpan_thrift_list() {
    decoder.readSpan(Codec.THRIFT.writeSpans(asList(span1, span2)));
  }

  @Test public void readSpans_thrift() {
    assertThat(decoder.readSpans(Codec.THRIFT.writeSpans(asList(span1, span2))))
      .containsExactly(span1, span2);
  }

  @Test(expected = IllegalArgumentException.class) public void readSpans_thrift_not_list() {
    decoder.readSpans(Codec.THRIFT.writeSpan(span1));
  }

  /** Single-element reads were for legacy non-list encoding. Don't add new code that does this */
  @Test(expected = UnsupportedOperationException.class) public void readSpan_json2() {
    decoder.readSpan(SpanBytesEncoder.JSON_V2.encode(span2_1));
  }

  @Test(expected = IllegalArgumentException.class) public void readSpans_json2_not_list() {
    decoder.readSpans(SpanBytesEncoder.JSON_V2.encode(span2_1));
  }

  @Test public void readSpans_json2() {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(asList(span2_1, span2_2));

    assertThat(decoder.readSpans(message))
      .containsExactly(span1, span2);
  }

  @Test(expected = IllegalArgumentException.class) public void readSpan_unknown() {
    decoder.readSpan(new byte[] {'h'});
  }

  @Test(expected = IllegalArgumentException.class) public void readSpans_unknown() {
    decoder.readSpans(new byte[] {'h'});
  }
}
