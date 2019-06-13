/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2;

import java.nio.ByteBuffer;
import zipkin2.codec.BytesDecoder;
import zipkin2.codec.SpanBytesDecoder;

/**
 * Detecting decoder used in transports which don't include means to identify the type of the data.
 *
 * <p>For example, we can identify the encoding and also the format in http via the request path and
 * content-type. However, in Kafka it could be that folks send mixed Zipkin data without identifying
 * its format. For example, Kafka historically has no content-type and users don't always segregate
 * different queues by instrumentation format.
 */
// In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
// .. If the first byte isn't in that range, it isn't a thrift.
//
// When byte(0) == '[' (91), assume it is a list of json-encoded spans
//
// When byte(0) == 10, assume it is a proto3-encoded span or trace ID field
//
// When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
// .. When serializing a Span (Struct), the first byte will be the type of a field
// .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
// .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
public final class SpanBytesDecoderDetector {
  /**
   * Zipkin v2 json will have "localEndpoint" or "remoteEndpoint" fields, and others won't.
   *
   * <p>Note: Technically, it is also possible that one can thwart this by creating an binary
   * annotation of type string with a name or value literally ending in Endpoint. This would be
   * strange, especially as the convention to identify a local endpoint is the key "lc". To prevent
   * a secondary check, this scenario is also ignored.
   */
  static final byte[] ENDPOINT_FIELD_SUFFIX = {'E', 'n', 'd', 'p', 'o', 'i', 'n', 't', '"'};

  /**
   * Technically, it is possible to have a v2 span with no endpoints. This should catch the case
   * where someone reported a tag without reporting the "localEndpoint".
   *
   * <p>Note: we don't check for annotations as that exists in both v1 and v2 formats.
   */
  static final byte[] TAGS_FIELD = {'"', 't', 'a', 'g', 's', '"'};

  /** @throws IllegalArgumentException if the input isn't a v1 json or thrift single-span message */
  public static BytesDecoder<Span> decoderForMessage(byte[] span) {
    BytesDecoder<Span> decoder = detectDecoder(ByteBuffer.wrap(span));
    if (span[0] == 12 /* List[ThriftSpan] */ || span[0] == '[') {
      throw new IllegalArgumentException("Expected json or thrift object, not list encoding");
    }
    if (decoder == SpanBytesDecoder.JSON_V2 || decoder == SpanBytesDecoder.PROTO3) {
      throw new UnsupportedOperationException("v2 formats should only be used with list messages");
    }
    return decoder;
  }

  /** @throws IllegalArgumentException if the input isn't a json, proto3 or thrift list message. */
  public static BytesDecoder<Span> decoderForListMessage(byte[] spans) {
    return decoderForListMessage(ByteBuffer.wrap(spans));
  }

  public static BytesDecoder<Span> decoderForListMessage(ByteBuffer spans) {
    BytesDecoder<Span> decoder = detectDecoder(spans);
    byte first = spans.get(spans.position());
    if (first != 12 /* List[ThriftSpan] */
      && first != 11 /* openzipkin/zipkin-reporter-java#133 */
      && !protobuf3(spans) && first != '[') {
      throw new IllegalArgumentException("Expected json, proto3 or thrift list encoding");
    }
    return decoder;
  }

  /** @throws IllegalArgumentException if the input isn't a json or thrift list or object. */
  static BytesDecoder<Span> detectDecoder(ByteBuffer bytes) {
    byte first = bytes.get(bytes.position());
    if (first <= 16) { // binary format
      if (protobuf3(bytes)) return SpanBytesDecoder.PROTO3;
      return SpanBytesDecoder.THRIFT; /* the first byte is the TType, in a range 0-16 */
    } else if (first != '[' && first != '{') {
      throw new IllegalArgumentException("Could not detect the span format");
    }
    if (contains(bytes, ENDPOINT_FIELD_SUFFIX)) return SpanBytesDecoder.JSON_V2;
    if (contains(bytes, TAGS_FIELD)) return SpanBytesDecoder.JSON_V2;
    return SpanBytesDecoder.JSON_V1;
  }

  static boolean contains(ByteBuffer bytes, byte[] subsequence) {
    bytes:
    for (int i = 0; i < bytes.remaining() - subsequence.length + 1; i++) {
      for (int j = 0; j < subsequence.length; j++) {
        if (bytes.get(bytes.position() + i + j) != subsequence[j]) {
          continue bytes;
        }
      }
      return true;
    }
    return false;
  }

  /* span key or trace ID key */
  static boolean protobuf3(ByteBuffer bytes) {
    // varint follows and won't be zero
    return bytes.get(bytes.position()) == 10 && bytes.get(bytes.position() + 1) != 0;
  }

  SpanBytesDecoderDetector() {}
}
