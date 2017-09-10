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
package zipkin2.internal;

import java.nio.charset.Charset;

public final class Buffer {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  public interface Writer<T> {
    int sizeInBytes(T value);

    void write(T value, Buffer buffer);
  }

  private final byte[] buf;
  int pos; // visible for testing

  public Buffer(int size) {
    buf = new byte[size];
  }

  Buffer(byte[] buf, int pos) {
    this.buf = buf;
    this.pos = pos;
  }

  public Buffer writeByte(int v) {
    buf[pos++] = (byte) v;
    return this;
  }

  public Buffer write(byte[] v) {
    System.arraycopy(v, 0, buf, pos, v.length);
    pos += v.length;
    return this;
  }

  static int utf8SizeInBytes(String string) {
    // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
    int sizeInBytes = 0;
    for (int i = 0, len = string.length(); i < len; i++) {
      char ch = string.charAt(i);
      if (ch < 0x80) {
        sizeInBytes++; // 7-bit character
      } else if (ch < 0x800) {
        sizeInBytes += 2; // 11-bit character
      } else if (ch < 0xd800 || ch > 0xdfff) {
        sizeInBytes += 3; // 16-bit character
      } else {
        // malformed surrogate logic borrowed from okio.Utf8
        int low = i + 1 < len ? string.charAt(i + 1) : 0;
        if (ch > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          sizeInBytes++; // A malformed surrogate, which yields '?'.
        } else {
          // A 21-bit character
          sizeInBytes += 4;
          i++;
        }
      }
    }
    return sizeInBytes;
  }

  public Buffer writeAscii(String v) {
    int length = v.length();
    for (int i = 0; i < length; i++) {
      buf[pos++] = (byte) v.charAt(i);
    }
    return this;
  }

  static boolean isAscii(String v) {
    for (int i = 0, length = v.length(); i < length; i++) {
      if (v.charAt(i) >= 0x80) {
        return false;
      }
    }
    return true;
  }

  public Buffer writeUtf8(String v) {
    if (isAscii(v)) return writeAscii(v);
    byte[] temp = v.getBytes(UTF_8);
    write(temp);
    return this;
  }

  /**
   * Binary search for character width which favors matching lower numbers.
   *
   * <p>Adapted from okio.Buffer
   */
  public static int asciiSizeInBytes(long v) {
    if (v == 0) return 1;
    if (v == Long.MIN_VALUE) return 20;

    boolean negative = false;
    if (v < 0) {
      v = -v; // making this positive allows us to compare using less-than
      negative = true;
    }
    int width =
      v < 100000000L
        ? v < 10000L
        ? v < 100L
        ? v < 10L ? 1 : 2
        : v < 1000L ? 3 : 4
        : v < 1000000L
          ? v < 100000L ? 5 : 6
          : v < 10000000L ? 7 : 8
        : v < 1000000000000L
          ? v < 10000000000L
          ? v < 1000000000L ? 9 : 10
          : v < 100000000000L ? 11 : 12
          : v < 1000000000000000L
            ? v < 10000000000000L ? 13
            : v < 100000000000000L ? 14 : 15
            : v < 100000000000000000L
              ? v < 10000000000000000L ? 16 : 17
              : v < 1000000000000000000L ? 18 : 19;
    return negative ? width + 1 : width; // conditionally add room for negative sign
  }

  public Buffer writeAscii(long v) {
    if (v == 0) return writeByte('0');
    if (v == Long.MIN_VALUE) return writeAscii("-9223372036854775808");

    int width = asciiSizeInBytes(v);
    int pos = this.pos += width; // We write backwards from right to left.

    boolean negative = false;
    if (v < 0) {
      negative = true;
      v = -v; // needs to be positive so we can use this for an array index
    }
    while (v != 0) {
      int digit = (int) (v % 10);
      buf[--pos] = DIGITS[digit];
      v /= 10;
    }
    if (negative) buf[--pos] = '-';
    return this;
  }

  static final byte[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

  public byte[] toByteArray() {
    //assert pos == buf.length;
    return buf;
  }
}
