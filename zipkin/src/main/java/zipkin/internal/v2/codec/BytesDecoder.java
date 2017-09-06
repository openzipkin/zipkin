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

/**
 * @param <T> type of the object to deserialize
 */
public interface BytesDecoder<T> {
  Encoding encoding();

  /** throws {@linkplain IllegalArgumentException} if the type couldn't be decoded */
  T decode(byte[] serialized);

  /** throws {@linkplain IllegalArgumentException} if the type couldn't be decoded */
  List<T> decodeList(byte[] serialized);
}
