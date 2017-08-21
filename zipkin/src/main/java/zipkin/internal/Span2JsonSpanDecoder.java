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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.internal.v2.codec.Decoder;

/** Decodes a span from zipkin v2 encoding */
public final class Span2JsonSpanDecoder implements SpanDecoder {
  @Override public Span readSpan(byte[] span) {
    throw new UnsupportedOperationException("current transports only accept list messages");
  }

  @Override public List<Span> readSpans(byte[] span) {
    List<Span2> span2s = Decoder.JSON.decodeList(span);
    if (span2s.isEmpty()) return Collections.emptyList();
    int length = span2s.size();
    List<Span> result = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      result.add(Span2Converter.toSpan(span2s.get(i)));
    }
    return result;
  }
}
