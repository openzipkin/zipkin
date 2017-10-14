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
package zipkin2.codec;

import java.util.Collection;
import java.util.List;
import zipkin2.internal.Nullable;

/**
 * This type accepts a collection that receives decoded elements.
 *
 * <pre>{@code
 * ArrayList<Span> out = new ArrayList<>();
 * SpanBytesDecoder.JSON_V2.decodeList(spans, out)
 * }</pre>
 *
 * @param <T> type of the object to deserialize
 */
// This receives a collection, not a function because it profiles 10% faster and all use cases are
// collections. This receives collection as opposed to list as zipkin-dependencies decodes into a
// set to dedupe redundant messages.
public interface BytesDecoder<T> {
  Encoding encoding();

  /**
   * This is used seldomly as the canonical message form is a {@link #decodeList(byte[], Collection)
   * list}.
   *
   * <p>Note: multiple elements can be consumed from a single serialized object. For example, if the
   * input is Zipkin v1, the list might receive two elements if the serialized object was a shared
   * span.
   *
   * @param serialized a single message, for example a json object
   * @return true if an element was decoded
   * @throws {@linkplain IllegalArgumentException} if the type couldn't be decoded
   */
  // used by zipkin-dependencies which reads elements one-at-a-time from ES documents
  boolean decode(byte[] serialized, Collection<T> out);

  /** Visible for testing. This returns the first element parsed from the serialized object or null */
  @Nullable T decodeOne(byte[] serialized);

  /**
   * @return true if an element was decoded
   * @throws {@linkplain IllegalArgumentException} if the type couldn't be decoded
   */
  boolean decodeList(byte[] serialized, Collection<T> out);

  /** Convenience method for {@link #decode(byte[], Collection)} */
  List<T> decodeList(byte[] serialized);
}
