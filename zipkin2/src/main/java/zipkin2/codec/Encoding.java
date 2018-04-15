/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
import zipkin2.internal.Buffer;

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
   * Repeated (type 2) fields are length-prefixed, the value is a concatenation with no additional
   * overhead.
   *
   * <p>See https://developers.google.com/protocol-buffers/docs/encoding#optional
   */
  PROTO3 {
    /** Returns the size of a length-prefixed field in a protobuf message */
    @Override public int listSizeInBytes(int encodedSizeInBytes) {
      return 1 // assumes field number <= 15
        + Buffer.varintSizeInBytes(encodedSizeInBytes) // bytes to encode the length
        + encodedSizeInBytes; // the actual length
    }

    /** Returns a concatenation of length-prefixed size for each value */
    @Override public int listSizeInBytes(List<byte[]> values) {
      int sizeInBytes = 0;
      for (int i = 0, length = values.size(); i < length; ) {
        sizeInBytes += listSizeInBytes(values.get(i++).length);
      }
      return sizeInBytes;
    }
  };

  /** Like {@link #listSizeInBytes(List)}, except for a single element. */
  public abstract int listSizeInBytes(int encodedSizeInBytes);

  public abstract int listSizeInBytes(List<byte[]> values);
}
