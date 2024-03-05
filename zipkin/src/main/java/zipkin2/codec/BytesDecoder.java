/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
   * This is used seldom as the canonical message form is a {@link #decodeList(byte[], Collection)
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

  /** Returns {@code true} if an element was decoded or throws {@link IllegalArgumentException}. */
  boolean decodeList(byte[] serialized, Collection<T> out);

  /** Convenience method for {@link #decodeList(byte[], Collection)} */
  List<T> decodeList(byte[] serialized);
}
