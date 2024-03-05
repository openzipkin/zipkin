/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

public final class RecyclableBuffers {
  RecyclableBuffers() {
  }

  static final ThreadLocal<char[]> SHORT_STRING_BUFFER = new ThreadLocal<>();
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
