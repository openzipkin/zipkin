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

import java.util.List;
import zipkin.Span;
import zipkin.SpanDecoder;

// In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
// .. If the first byte isn't in that range, it isn't a thrift.
//
// When byte(0) == '[' (91), assume it is a list of json-encoded spans
//
// When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
// .. When serializing a Span (Struct), the first byte will be the type of a field
// .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
// .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
public final class DetectingSpanDecoder implements SpanDecoder {
  /** zipkin v2 will have this tag, and others won't. */
  static final byte[] LOCAL_ENDPOINT_TAG = "\"localEndpoint\"".getBytes(Util.UTF_8);
  static final SpanDecoder JSON2_DECODER = new V2JsonSpanDecoder();

  @Override public Span readSpan(byte[] span) {
    SpanDecoder decoder = detectFormat(span);
    if (span[0] == 12 /* List[ThriftSpan] */ || span[0] == '[') {
      throw new IllegalArgumentException("Expected json or thrift object, not list encoding");
    }
    return decoder.readSpan(span);
  }

  @Override public List<Span> readSpans(byte[] span) {
    SpanDecoder decoder = detectFormat(span);
    if (span[0] != 12 /* List[ThriftSpan] */ && span[0] != '[') {
      throw new IllegalArgumentException("Expected json or thrift list encoding");
    }
    return decoder.readSpans(span);
  }

  /** @throws IllegalArgumentException if the input isn't a json or thrift list or object. */
  public static SpanDecoder detectFormat(byte[] bytes) {
    if (bytes[0] <= 16 /* the first byte is the TType, in a range 0-16 */) {
      return THRIFT_DECODER;
    } else if (bytes[0] != '[' && bytes[0] != '{') {
      throw new IllegalArgumentException("Could not detect the span format");
    }
    bytes:
    for (int i = 0; i < bytes.length - LOCAL_ENDPOINT_TAG.length + 1; i++) {
      for (int j = 0; j < LOCAL_ENDPOINT_TAG.length; j++) {
        if (bytes[i + j] != LOCAL_ENDPOINT_TAG[j]) {
          continue bytes;
        }
      }
      return JSON2_DECODER;
    }
    return SpanDecoder.JSON_DECODER;
  }
}
