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
import zipkin.SpanDecoder;
import zipkin2.codec.SpanBytesDecoder;

/** Decodes a span from zipkin v2 encoding */
public final class V2JsonSpanDecoder implements SpanDecoder {
  @Override public zipkin.Span readSpan(byte[] span) {
    throw new UnsupportedOperationException("current transports only accept list messages");
  }

  @Override public List<zipkin.Span> readSpans(byte[] span) {
    List result = new ArrayList<>();
    if (!SpanBytesDecoder.JSON_V2.decodeList(span, result)) return Collections.emptyList();
    for (int i = 0, length = result.size(); i < length; i++) {
      result.set(i, V2SpanConverter.toSpan((zipkin2.Span) result.get(i)));
    }
    return result;
  }
}
