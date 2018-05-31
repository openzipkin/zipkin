/*
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
package zipkin.internal;

public final class Buffer {
  public interface Writer<T> {
    int sizeInBytes(T value);

    void write(T value, Buffer buffer);
  }

  private final byte[] buf;
  private int pos;

  Buffer(int size) {
    buf = new byte[size];
  }

  public Buffer writeByte(int v) {
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

  byte[] toByteArray() {
    //assert pos == buf.length;
    return buf;
  }
}
