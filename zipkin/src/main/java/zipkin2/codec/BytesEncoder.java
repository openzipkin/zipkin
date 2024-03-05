/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import java.util.List;

/**
 * Utility for encoding one or more elements of a type into a byte array.
 *
 * @param <T> type of the object to encode
 */
public interface BytesEncoder<T> {
  Encoding encoding();

  int sizeInBytes(T input);

  /** Serializes an object into its binary form. */
  byte[] encode(T input);

  /** Serializes a list of objects into their binary form. */
  byte[] encodeList(List<T> input);
}
