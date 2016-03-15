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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Codec;
import zipkin.Span;

/**
 * Conditionally decodes depending on whether the input bytes are encoded as a single span or a
 * list. Malformed input is ignored.
 */
final class SpansDecoder implements Decoder<List<Span>> {

  @Override
  public List<Span> fromBytes(byte[] bytes) {
    try {
      // Given the thrift encoding is TBinaryProtocol..
      // .. When serializing a Span (Struct), the first byte will be the type of a field
      // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT
      // Span has no STRUCT fields: we assume that if the first byte is TType.STRUCT is a list.
      if (bytes[0] == 12 /* TType.STRUCT */) {
        return Codec.THRIFT.readSpans(bytes);
      } else {
        return Collections.singletonList(Codec.THRIFT.readSpan(bytes));
      }
    } catch (IllegalArgumentException ignored) {
      // binary decoding messages aren't useful enough to clutter logs with.
      return Collections.emptyList();
    }
  }
}
