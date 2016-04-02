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
package zipkin.kafka;

import java.util.Collections;
import java.util.List;
import kafka.serializer.Decoder;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.SpanConsumerLogger;

/**
 * Conditionally decodes depending on whether the input bytes are encoded as a single span or a
 * list. Malformed input is ignored.
 */
final class SpansDecoder implements Decoder<List<Span>> {
  final SpanConsumerLogger logger = new SpanConsumerLogger(KafkaStreamProcessor.class);

  @Override
  public List<Span> fromBytes(byte[] bytes) {
    try {
      // In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
      // .. If the first byte isn't in that range, it isn't a thrift.
      //
      // When byte(0) == '[' (91), assume it is a list of json-encoded spans
      //
      // When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
      // .. When serializing a Span (Struct), the first byte will be the type of a field
      // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
      // .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
      if (bytes[0] == '[') {
        return Codec.JSON.readSpans(bytes);
      } else if (bytes[0] == 12 /* TType.STRUCT */) {
        return Codec.THRIFT.readSpans(bytes);
      } else {
        return Collections.singletonList(Codec.THRIFT.readSpan(bytes));
      }
    } catch (RuntimeException e) {
      logger.errorDecoding(e);
      return Collections.emptyList();
    }
  }
}
