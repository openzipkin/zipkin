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

import java.util.List;

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
  };

  /** Like {@link #listSizeInBytes(List)}, except for a single element. */
  public abstract int listSizeInBytes(int encodedSizeInBytes);

  public abstract int listSizeInBytes(List<byte[]> values);
}
