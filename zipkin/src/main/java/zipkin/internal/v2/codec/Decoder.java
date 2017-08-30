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

/**
 * @param <S> type of the span, usually {@link zipkin.Span}
 */
public interface Decoder<S> {
  Decoder<Span> JSON = new Decoder<Span>() {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public List<Span> decodeList(byte[] span) {
      return JsonCodec.readList(new Span2JsonAdapters.Span2Reader(), span);
    }

    @Override public List<List<Span>> decodeNestedList(byte[] span) {
      return JsonCodec.readList(new Span2JsonAdapters.Span2ListReader(), span);
    }
  };

  Encoding encoding();

  /** throws {@linkplain IllegalArgumentException} if the spans couldn't be decoded */
  List<S> decodeList(byte[] span);

  /** throws {@linkplain IllegalArgumentException} if the spans couldn't be decoded */
  List<List<S>> decodeNestedList(byte[] span);
}
