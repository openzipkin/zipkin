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

package zipkin;

import java.util.List;
import zipkin.internal.DetectingSpanDecoder;
import zipkin.internal.JsonCodec;
import zipkin.internal.ThriftCodec;

/** Decodes spans from serialized bytes. */
public interface SpanDecoder {
  SpanDecoder JSON_DECODER = new JsonCodec();
  SpanDecoder THRIFT_DECODER = new ThriftCodec();
  /** Detects the format of the encoded spans or throws IllegalArgumentException */
  SpanDecoder DETECTING_DECODER = new DetectingSpanDecoder();

  /** throws {@linkplain IllegalArgumentException} if a span couldn't be decoded */
  Span readSpan(byte[] span);

  /** throws {@linkplain IllegalArgumentException} if the spans couldn't be decoded */
  List<Span> readSpans(byte[] span);
}
