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
package zipkin2.codec;

import java.util.List;

// ZIPKIN3 make this not an enum as it prevents non-standard encoding, for example reporting to
// DataDog which has a message pack encoding.
public enum Encoding {
  JSON {
    /** Encoding overhead of a single element is brackets */
    @Override public int listSizeInBytes(int encodedSizeInBytes) {
      return 2 + encodedSizeInBytes;
    }

    /** Encoding overhead is brackets and a comma for each span over 1 */
    @Override public int listSizeInBytes(List<byte[]> values) {
      int sizeInBytes = 2; // brackets
      for (int i = 0, length = values.size(); i < length; ) {
        sizeInBytes += values.get(i++).length;
        if (i < length) sizeInBytes++;
      }
      return sizeInBytes;
    }
  },
  /**
   * The first format of Zipkin was TBinaryProtocol, big-endian thrift. It is no longer used, but
   * defined here to allow collectors to support reading old data.
   *
   * <p>The message's binary data includes a list header followed by N spans serialized in
   * TBinaryProtocol
   *
   * @deprecated this format is deprecated in favor of json or proto3
   */
  @Deprecated
  THRIFT {
    /** Encoding overhead is thrift type plus 32-bit length prefix */
    @Override public int listSizeInBytes(int encodedSizeInBytes) {
      return 5 + encodedSizeInBytes;
    }

    /** Encoding overhead is thrift type plus 32-bit length prefix */
    @Override public int listSizeInBytes(List<byte[]> values) {
      int sizeInBytes = 5;
      for (int i = 0, length = values.size(); i < length; i++) {
        sizeInBytes += values.get(i).length;
      }
      return sizeInBytes;
    }
  },
  /**
   * Repeated (type 2) fields are length-prefixed. A list is a concatenation of fields with no
   * additional overhead.
   *
   * <p>See https://developers.google.com/protocol-buffers/docs/encoding#optional
   */
  PROTO3 {
    /** Returns the input as it is assumed to be length-prefixed field from a protobuf message */
    @Override public int listSizeInBytes(int encodedSizeInBytes) {
      return encodedSizeInBytes;
    }

    /** Returns a concatenation of sizes */
    @Override public int listSizeInBytes(List<byte[]> values) {
      int sizeInBytes = 0;
      for (int i = 0, length = values.size(); i < length; ) {
        sizeInBytes += values.get(i++).length;
      }
      return sizeInBytes;
    }
  };

  /** Like {@link #listSizeInBytes(List)}, except for a single element. */
  public abstract int listSizeInBytes(int encodedSizeInBytes);

  public abstract int listSizeInBytes(List<byte[]> values);
}
