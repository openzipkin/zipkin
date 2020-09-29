/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.internal;

public final class RecyclableBuffers {
  RecyclableBuffers() {
  }

  static final ThreadLocal<char[]> SHORT_STRING_BUFFER = new ThreadLocal<char[]>();
  /**
   * Maximum character length constraint of most names, IP literals and IDs.
   */
  public static final int SHORT_STRING_LENGTH = 256;

  /**
   * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes into hex, IP
   * literals, or {@link #SHORT_STRING_LENGTH short strings}. The buffer must never be leaked
   * outside the method. Most will {@link String#String(char[], int, int) copy it into a string}.
   */
  public static char[] shortStringBuffer() {
    char[] shortStringBuffer = SHORT_STRING_BUFFER.get();
    if (shortStringBuffer == null) {
      shortStringBuffer = new char[SHORT_STRING_LENGTH];
      SHORT_STRING_BUFFER.set(shortStringBuffer);
    }
    return shortStringBuffer;
  }
}
