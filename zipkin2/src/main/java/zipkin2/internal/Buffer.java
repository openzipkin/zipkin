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

public final class Buffer {
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

  /**
   * This returns the bytes needed to transcode a UTF-16 Java String to UTF-8 bytes.
   *
   * <p>Originally based on http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
   * <p>Later, ASCII run and malformed surrogate logic borrowed from okio.Utf8
   */
  static int utf8SizeInBytes(String string) {
    int sizeInBytes = 0;
    for (int i = 0, len = string.length(); i < len; i++) {
      char ch = string.charAt(i);
      if (ch < 0x80) {
        sizeInBytes++; // 7-bit ASCII character
        // This could be an ASCII run, or possibly entirely ASCII
        while (i < len - 1) {
          ch = string.charAt(i + 1);
          if (ch >= 0x80) break;
          i++;
          sizeInBytes++; // another 7-bit ASCII character
        }
      } else if (ch < 0x800) {
        sizeInBytes += 2; // 11-bit character
      } else if (ch < 0xd800 || ch > 0xdfff) {
        sizeInBytes += 3; // 16-bit character
      } else {
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
    for (int i = 0, len = v.length(); i < len; i++) {
      buf[pos++] = (byte) v.charAt(i);
    }
    return this;
  }

  /**
   * This transcodes a UTF-16 Java String to UTF-8 bytes.
   *
   * <p>This looks most similar to {@code io.netty.buffer.ByteBufUtil.writeUtf8(AbstractByteBuf, int, CharSequence, int)}
   * v4.1, modified including features to address ASCII runs of text.
   */
  public Buffer writeUtf8(String string) {
    for (int i = 0, len = string.length(); i < len; i++) {
      char ch = string.charAt(i);
      if (ch < 0x80) { // 7-bit ASCII character
        buf[pos++] = (byte) ch;
        // This could be an ASCII run, or possibly entirely ASCII
        while (i < len - 1) {
          ch = string.charAt(i + 1);
          if (ch >= 0x80) break;
          i++;
          buf[pos++] = (byte) ch; // another 7-bit ASCII character
        }
      } else if (ch < 0x800) {  // 11-bit character
        buf[pos++] = (byte) (0xc0 | (ch >> 6));
        buf[pos++] = (byte) (0x80 | (ch & 0x3f));
      } else if (ch < 0xd800 || ch > 0xdfff) { // 16-bit character
        buf[pos++] = (byte) (0xe0 | (ch >> 12));
        buf[pos++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
        buf[pos++] = (byte) (0x80 | (ch & 0x3f));
      } else { // Possibly a 21-bit character
        if (!Character.isHighSurrogate(ch)) { // Malformed or not UTF-8
          buf[pos++] = '?';
          continue;
        }
        if (i == len - 1) { // Truncated or not UTF-8
          buf[pos++] = '?';
          break;
        }
        char low = string.charAt(++i);
        if (!Character.isLowSurrogate(low)) { // Malformed or not UTF-8
          buf[pos++] = '?';
          buf[pos++] = (byte) (Character.isHighSurrogate(low) ? '?' : low);
          continue;
        }
        // Write the 21-bit character using 4 bytes
        // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630
        int codePoint = Character.toCodePoint(ch, low);
        buf[pos++] = (byte) (0xf0 | (codePoint >> 18));
        buf[pos++] = (byte) (0x80 | ((codePoint >> 12) & 0x3f));
        buf[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3f));
        buf[pos++] = (byte) (0x80 | (codePoint & 0x3f));
      }
    }
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
