/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.internal;

final class Buffer {
  interface Writer<T> {
    int sizeInBytes(T value);

    void write(T value, Buffer buffer);
  }

  private final byte[] buf;
  private int pos;

  Buffer(int size) {
    buf = new byte[size];
  }

  Buffer writeByte(int v) {
    buf[pos++] = (byte) v;
    return this;
  }

  Buffer write(byte[] v) {
    System.arraycopy(v, 0, buf, pos, v.length);
    pos += v.length;
    return this;
  }

  Buffer writeShort(int v) {
    writeByte((v >>> 8L) & 0xff);
    writeByte(v & 0xff);
    return this;
  }

  Buffer writeInt(int v) {
    buf[pos++] = (byte) ((v >>> 24L) & 0xff);
    buf[pos++] = (byte) ((v >>> 16L) & 0xff);
    buf[pos++] = (byte) ((v >>> 8L) & 0xff);
    buf[pos++] = (byte) (v & 0xff);
    return this;
  }

  Buffer writeLong(long v) {
    buf[pos++] = (byte) ((v >>> 56L) & 0xff);
    buf[pos++] = (byte) ((v >>> 48L) & 0xff);
    buf[pos++] = (byte) ((v >>> 40L) & 0xff);
    buf[pos++] = (byte) ((v >>> 32L) & 0xff);
    buf[pos++] = (byte) ((v >>> 24L) & 0xff);
    buf[pos++] = (byte) ((v >>> 16L) & 0xff);
    buf[pos++] = (byte) ((v >>> 8L) & 0xff);
    buf[pos++] = (byte) (v & 0xff);
    return this;
  }

  static int asciiSizeInBytes(String string) {
    return string.length();
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
      } else if (Character.isHighSurrogate(ch)) {
        sizeInBytes += 4; // Must be largest UTF-8 character
        i++;
      } else {
        sizeInBytes += 3; // Assume 16-bit character
      }
    }
    return sizeInBytes;
  }

  /** Writes a length-prefixed string */
  Buffer writeLengthPrefixed(String v) {
    boolean ascii = isAscii(v);
    if (ascii) {
      writeInt(v.length());
      return writeAscii(v);
    } else {
      byte[] temp = v.getBytes(Util.UTF_8);
      writeInt(temp.length);
      write(temp);
    }
    return this;
  }

  Buffer writeAscii(String v) {
    int length = v.length();
    for (char i = 0; i < length; i++) {
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

  /*
   * Escaping logic adapted from Moshi, which we couldn't use due to language level
   *
   * From RFC 7159, "All Unicode characters may be placed within the
   * quotation marks except for the characters that must be escaped:
   * quotation mark, reverse solidus, and the control characters
   * (U+0000 through U+001F)."
   *
   * We also escape '\u2028' and '\u2029', which JavaScript interprets as
   * newline characters. This prevents eval() from failing with a syntax
   * error. http://code.google.com/p/google-gson/issues/detail?id=341
   */
  private static final String[] REPLACEMENT_CHARS;

  static {
    REPLACEMENT_CHARS = new String[128];
    for (int i = 0; i <= 0x1f; i++) {
      REPLACEMENT_CHARS[i] = String.format("\\u%04x", (int) i);
    }
    REPLACEMENT_CHARS['"'] = "\\\"";
    REPLACEMENT_CHARS['\\'] = "\\\\";
    REPLACEMENT_CHARS['\t'] = "\\t";
    REPLACEMENT_CHARS['\b'] = "\\b";
    REPLACEMENT_CHARS['\n'] = "\\n";
    REPLACEMENT_CHARS['\r'] = "\\r";
    REPLACEMENT_CHARS['\f'] = "\\f";
  }

  private static final String U2028 = "\\u2028";
  private static final String U2029 = "\\u2029";

  static int jsonEscapedSizeInBytes(byte[] v) {
    for (int i = 0; i < v.length; i++) {
      int current = v[i] & 0xFF;
      if (i >= 2 &&
          // Is this the end of a u2028 or u2028 UTF-8 codepoint?
          // 0xE2 0x80 0xA8 == u2028; 0xE2 0x80 0xA9 == u2028
          (current == 0xA8 || current == 0xA9)
          && (v[i - 1] & 0xFF) == 0x80
          && (v[i - 2] & 0xFF) == 0xE2) {
        return jsonEscapedSizeInBytes(new String(v, Util.UTF_8));
      } else if (current < 0x80) {
        if (REPLACEMENT_CHARS[current] != null) {
          return jsonEscapedSizeInBytes(new String(v, Util.UTF_8));
        }
      }
    }
    return v.length; // must be a string we don't need to escape.
  }

  static int jsonEscapedSizeInBytes(String v) {
    boolean ascii = true;
    int escapingOverhead = 0;
    for (int i = 0, length = v.length(); i < length; i++) {
      char c = v.charAt(i);
      if (c == '\u2028' || c == '\u2029') {
        escapingOverhead += 5;
      } else if (c >= 0x80) {
        ascii = false;
      } else {
        String maybeReplacement = REPLACEMENT_CHARS[c];
        if (maybeReplacement != null) escapingOverhead += maybeReplacement.length() - 1;
      }
    }
    if (ascii) return asciiSizeInBytes(v) + escapingOverhead;
    return utf8SizeInBytes(v) + escapingOverhead;
  }

  Buffer writeJsonEscaped(String v) {
    int afterReplacement = 0;
    int length = v.length();
    StringBuilder builder = null;
    for (int i = 0; i < length; i++) {
      char c = v.charAt(i);
      String replacement;
      if (c < 0x80) {
        replacement = REPLACEMENT_CHARS[c];
        if (replacement == null) continue;
      } else if (c == '\u2028') {
        replacement = U2028;
      } else if (c == '\u2029') {
        replacement = U2029;
      } else {
        continue;
      }
      if (afterReplacement < i) { // write characters between the last replacement and now
        if (builder == null) builder = new StringBuilder();
        builder.append(v, afterReplacement, i);
      }
      if (builder == null) builder = new StringBuilder();
      builder.append(replacement);
      afterReplacement = i + 1;
    }
    if (builder == null) { // then we didn't escape anything
      return writeUtf8(v);
    }
    if (afterReplacement < length) {
      builder.append(v, afterReplacement, length);
    }
    return writeUtf8(builder.toString());
  }

  Buffer writeUtf8(String v) {
    if (isAscii(v)) return writeAscii(v);
    byte[] temp = v.getBytes(Util.UTF_8);
    write(temp);
    return this;
  }

  Buffer writeLowerHex(long v) {
    writeHexByte((byte) ((v >>> 56L) & 0xff));
    writeHexByte((byte) ((v >>> 48L) & 0xff));
    writeHexByte((byte) ((v >>> 40L) & 0xff));
    writeHexByte((byte) ((v >>> 32L) & 0xff));
    writeHexByte((byte) ((v >>> 24L) & 0xff));
    writeHexByte((byte) ((v >>> 16L) & 0xff));
    writeHexByte((byte) ((v >>> 8L) & 0xff));
    writeHexByte((byte) (v & 0xff));
    return this;
  }

  // the code to get the size of ipv6 is long and basically the same as encoding it.
  static int ipv6SizeInBytes(byte[] ipv6) {
    int result = IPV6_SIZE.get().writeIpV6(ipv6).pos;
    IPV6_SIZE.get().pos = 0;
    return result;
  }

  private static final ThreadLocal<Buffer> IPV6_SIZE = new ThreadLocal<Buffer>() {
    @Override protected Buffer initialValue() {
      return new Buffer(39); // maximum length of encoded ipv6
    }
  };

  Buffer writeIpV6(byte[] ipv6) {
    // Compress the longest string of zeros
    int zeroCompressionIndex = -1;
    int zeroCompressionLength = -1;
    int zeroIndex = -1;
    boolean allZeros = true;
    for (int i = 0; i < ipv6.length; i += 2) {
      if (ipv6[i] == 0 && ipv6[i + 1] == 0) {
        if (zeroIndex < 0) zeroIndex = i;
        continue;
      }
      allZeros = false;
      if (zeroIndex >= 0) {
        int zeroLength = i - zeroIndex;
        if (zeroLength > zeroCompressionLength) {
          zeroCompressionIndex = zeroIndex;
          zeroCompressionLength = zeroLength;
        }
        zeroIndex = -1;
      }
    }

    // handle all zeros: 0:0:0:0:0:0:0:0 -> ::
    if (allZeros) {
      buf[pos++] = ':';
      buf[pos++] = ':';
      return this;
    }

    // handle trailing zeros: 2001:0:0:4:0:0:0:0 -> 2001:0:0:4::
    if (zeroCompressionIndex == -1 && zeroIndex != -1) {
      zeroCompressionIndex = zeroIndex;
      zeroCompressionLength = 16 - zeroIndex;
    }

    int i = 0;
    while (i < ipv6.length) {
      if (i == zeroCompressionIndex) {
        buf[pos++] = ':';
        i += zeroCompressionLength;
        if (i == ipv6.length) buf[pos++] = ':';
        continue;
      }
      if (i != 0) buf[pos++] = ':';

      byte high = ipv6[i++];
      byte low = ipv6[i++];

      // handle leading zeros: 2001:0:0:4:0000:0:0:8 -> 2001:0:0:4::8
      boolean leadingZero;
      byte val = HEX_DIGITS[(high >> 4) & 0xf];
      if (!(leadingZero = val == '0')) buf[pos++] = val;
      val = HEX_DIGITS[high & 0xf];
      if (!(leadingZero = (leadingZero && val == '0'))) buf[pos++] = val;
      val = HEX_DIGITS[(low >> 4) & 0xf];
      if (!(leadingZero && val == '0')) buf[pos++] = val;
      buf[pos++] = HEX_DIGITS[low & 0xf];
    }
    return this;
  }

  /**
   * Binary search for character width which favors matching lower numbers.
   *
   * <p>Adapted from okio.Buffer
   */
  static int asciiSizeInBytes(long v) {
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

  Buffer writeAscii(long v) {
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
      buf[--pos] = HEX_DIGITS[digit];
      v /= 10;
    }
    if (negative) buf[--pos] = '-';
    return this;
  }

  static final byte[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  void writeHexByte(byte b) {
    buf[pos++] = HEX_DIGITS[(b >> 4) & 0xf];
    buf[pos++] = HEX_DIGITS[b & 0xf];
  }

  static final byte[] URL_MAP = new byte[] {
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
      'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
      'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4',
      '5', '6', '7', '8', '9', '-', '_'
  };

  static int base64UrlSizeInBytes(byte[] in) {
    return (in.length + 2) / 3 * 4;
  }

  /**
   * Adapted from okio.Base64 as JRE 6 doesn't have a base64Url encoder
   *
   * <p>Original author: Alexander Y. Kleymenov
   */
  Buffer writeBase64Url(byte[] in) {
    int end = in.length - in.length % 3;
    for (int i = 0; i < end; i += 3) {
      buf[pos++] = URL_MAP[(in[i] & 0xff) >> 2];
      buf[pos++] = URL_MAP[((in[i] & 0x03) << 4) | ((in[i + 1] & 0xff) >> 4)];
      buf[pos++] = URL_MAP[((in[i + 1] & 0x0f) << 2) | ((in[i + 2] & 0xff) >> 6)];
      buf[pos++] = URL_MAP[(in[i + 2] & 0x3f)];
    }
    switch (in.length % 3) {
      case 1:
        buf[pos++] = URL_MAP[(in[end] & 0xff) >> 2];
        buf[pos++] = URL_MAP[(in[end] & 0x03) << 4];
        buf[pos++] = '=';
        buf[pos++] = '=';
        break;
      case 2:
        buf[pos++] = URL_MAP[(in[end] & 0xff) >> 2];
        buf[pos++] = URL_MAP[((in[end] & 0x03) << 4) | ((in[end + 1] & 0xff) >> 4)];
        buf[pos++] = URL_MAP[((in[end + 1] & 0x0f) << 2)];
        buf[pos++] = '=';
        break;
    }
    return this;
  }

  byte[] toByteArray() {
    //assert pos == buf.length;
    return buf;
  }
}
