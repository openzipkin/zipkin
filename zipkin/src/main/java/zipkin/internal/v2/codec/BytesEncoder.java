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
package zipkin.internal.v2.codec;

import java.util.List;
import zipkin.internal.JsonCodec;
import zipkin.internal.v2.Span;

import static zipkin.internal.v2.codec.Span2JsonAdapters.SPAN_WRITER;

/**
 * @param <S> type of the span, usually {@link zipkin.Span}
 */
public interface BytesEncoder<S> {
  BytesEncoder<Span> JSON = new BytesEncoder<Span>() {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public byte[] encode(Span span) {
      return JsonCodec.write(SPAN_WRITER, span);
    }

    @Override public byte[] encodeList(List<Span> spans) {
      return JsonCodec.writeList(SPAN_WRITER, spans);
    }

    @Override public byte[] encodeNestedList(List<List<Span>> spans) {
      return JsonCodec.writeNestedList(SPAN_WRITER, spans);
    }
  };

  Encoding encoding();

  /** Serializes a span recorded from instrumentation into its binary form. */
  byte[] encode(S span);

  /** Serializes a list of spans recorded from instrumentation into its binary form. */
  byte[] encodeList(List<S> spans);

  /** Serializes a list of spans recorded from instrumentation into its binary form. */
  byte[] encodeNestedList(List<List<S>> traces);
}
