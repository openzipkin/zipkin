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
package zipkin2.internal;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static zipkin2.internal.HexCodec.HEX_DIGITS;
import static zipkin2.internal.JsonCodec.UTF_8;

/** Read operations do bounds checks, as typically more errors occur reading than writing. */
public abstract class ReadBuffer extends InputStream {

  /** Do not use the buffer passed here after, as it may be manipulated directly. */
  public static ReadBuffer wrapUnsafe(ByteBuffer buffer) {
    if (buffer.hasArray()) {
      int offset = buffer.arrayOffset() + buffer.position();
      return wrap(buffer.array(), offset, buffer.remaining());
    }
    return buffer.order() == ByteOrder.BIG_ENDIAN
      ? new BigEndianByteBuffer(buffer)
      : new LittleEndianByteBuffer(buffer);
  }

  public static ReadBuffer wrap(byte[] bytes) {
    return wrap(bytes, 0, bytes.length);
  }

  public static ReadBuffer wrap(byte[] bytes, int pos, int length) {
    return new ReadBuffer.Array(bytes, pos, length);
  }

  static final class BigEndianByteBuffer extends Buff {
    BigEndianByteBuffer(ByteBuffer buf) {
      super(buf);
    }

    @Override short readShort() {
      require(2);
      return buf.getShort();
    }

    @Override int readInt() {
      require(4);
      return buf.getInt();
    }

    @Override long readLong() {
      require(8);
      return buf.getLong();
    }

    @Override long readLongLe() {
      return Long.reverseBytes(readLong());
    }
  }

  static final class LittleEndianByteBuffer extends Buff {
    LittleEndianByteBuffer(ByteBuffer buf) {
      super(buf);
    }

    @Override short readShort() {
      require(2);
      return Short.reverseBytes(buf.getShort());
    }

    @Override int readInt() {
      require(4);
      return Integer.reverseBytes(buf.getInt());
    }

    @Override long readLong() {
      return Long.reverseBytes(readLongLe());
    }

    @Override long readLongLe() {
      require(8);
      return buf.getLong();
    }
  }

  static abstract class Buff extends ReadBuffer {
    final ByteBuffer buf; // visible for testing

    Buff(ByteBuffer buf) {
      this.buf = buf;
    }

    @Override final byte readByteUnsafe() {
      return buf.get();
    }

    @Override final byte[] readBytes(int length) {
      require(length);
      byte[] copy = new byte[length];
      buf.get(copy);
      return copy;
    }

    @Override boolean tryReadAscii(char[] destination, int length) {
      buf.mark();
      for (int i = 0; i < length; i++) {
        byte b = buf.get();
        if ((b & 0x80) != 0) {
          buf.reset();
          return false;  // Not 7-bit ASCII character
        }
        destination[i] = (char) b;
      }
      return true;
    }

    @Override final String doReadUtf8(int length) {
      return new String(readBytes(length), UTF_8);
    }

    @Override public int pos() {
      return buf.position();
    }

    @Override public int read(byte[] dst, int offset, int length) {
      if (available() == 0) return -1;
      int toRead = checkReadArguments(dst, offset, length);
      if (toRead == 0) return 0;
      buf.get(dst, offset, toRead);
      return toRead;
    }

    @Override public long skip(long maxCount) {
      int skipped = Math.max(available(), (int) maxCount);
      buf.position(buf.position() + skipped);
      return skipped;
    }

    @Override public int available() {
      return buf.remaining();
    }
  }

  static final class Array extends ReadBuffer {
    final byte[] buf;
    int arrayOffset, offset, length;

    Array(byte[] buf, int offset, int length) {
      this.buf = buf;
      this.arrayOffset = this.offset = offset;
      this.length = length;
    }

    @Override final byte readByteUnsafe() {
      return buf[offset++];
    }

    @Override final byte[] readBytes(int length) {
      require(length);
      byte[] result = new byte[length];
      System.arraycopy(buf, offset, result, 0, length);
      offset += length;
      return result;
    }

    @Override public int read(byte[] dst, int offset, int length) {
      if (available() == 0) return -1;
      int toRead = checkReadArguments(dst, offset, length);
      if (toRead == 0) return 0;
      System.arraycopy(buf, this.offset, dst, 0, toRead);
      this.offset += toRead;
      return toRead;
    }

    @Override boolean tryReadAscii(char[] destination, int length) {
      for (int i = 0; i < length; i++) {
        byte b = buf[offset + i];
        if ((b & 0x80) != 0) return false;  // Not 7-bit ASCII character
        destination[i] = (char) b;
      }
      offset += length;
      return true;
    }

    @Override final String doReadUtf8(int length) {
      String result = new String(buf, offset, length, UTF_8);
      offset += length;
      return result;
    }

    @Override short readShort() {
      require(2);
      return (short) ((buf[offset++] & 0xff) << 8 | (buf[offset++] & 0xff));
    }

    @Override int readInt() {
      require(4);
      int pos = this.offset;
      this.offset = pos + 4;
      return (buf[pos] & 0xff) << 24
        | (buf[pos + 1] & 0xff) << 16
        | (buf[pos + 2] & 0xff) << 8
        | (buf[pos + 3] & 0xff);
    }

    /** Code is optimized for little endian as proto is the encouraged format. */
    @Override long readLong() {
      return Long.reverseBytes(readLongLe());
    }

    @Override long readLongLe() {
      require(8);
      int pos = this.offset;
      this.offset = pos + 8;
      return (buf[pos] & 0xffL)
        | (buf[pos + 1] & 0xffL) << 8
        | (buf[pos + 2] & 0xffL) << 16
        | (buf[pos + 3] & 0xffL) << 24
        | (buf[pos + 4] & 0xffL) << 32
        | (buf[pos + 5] & 0xffL) << 40
        | (buf[pos + 6] & 0xffL) << 48
        | (buf[pos + 7] & 0xffL) << 56;
    }

    @Override public int pos() {
      return offset - arrayOffset;
    }

    @Override public long skip(long maxCount) {
      int toSkip = Math.min(available(), (int) maxCount);
      offset += toSkip;
      return toSkip;
    }

    @Override public int available() {
      return length - (offset - arrayOffset);
    }
  }

  @Override public abstract int read(byte[] dst, int offset, int length);

  @Override public abstract long skip(long n);

  @Override public abstract int available();

  @Override public void close() {
  }

  @Override public void mark(int readlimit) {
    throw new UnsupportedOperationException();
  }

  @Override public synchronized void reset() {
    throw new UnsupportedOperationException();
  }

  @Override public boolean markSupported() {
    return false;
  }

  /** only use when you've already ensured the length you need is available */
  abstract byte readByteUnsafe();

  final byte readByte() {
    require(1);
    return readByteUnsafe();
  }

  abstract byte[] readBytes(int length);

  final String readUtf8(int length) {
    if (length == 0) return ""; // ex error tag with no value
    require(length);
    if (length > Platform.SHORT_STRING_LENGTH) return doReadUtf8(length);

    // Speculatively assume all 7-bit ASCII characters.. common in normal tags and names
    char[] buffer = Platform.shortStringBuffer();
    if (tryReadAscii(buffer, length)) {
      return new String(buffer, 0, length);
    }
    return doReadUtf8(length);
  }

  abstract boolean tryReadAscii(char[] destination, int length);

  abstract String doReadUtf8(int length);

  abstract int pos();

  abstract short readShort();

  abstract int readInt();

  abstract long readLong();

  abstract long readLongLe();

  @Override public final int read() {
    return available() > 0 ? readByteUnsafe() : -1;
  }

  final String readBytesAsHex(int length) {
    // All our hex fields are at most 32 characters.
    if (length > 32) {
      throw new IllegalArgumentException("hex field greater than 32 chars long: " + length);
    }

    require(length);
    char[] result = Platform.shortStringBuffer();
    int hexLength = length * 2;
    for (int i = 0; i < hexLength; i += 2) {
      byte b = readByteUnsafe();
      result[i + 0] = HEX_DIGITS[(b >> 4) & 0xf];
      result[i + 1] = HEX_DIGITS[b & 0xf];
    }
    return new String(result, 0, hexLength);
  }

  /**
   * @return the value read. Use {@link WriteBuffer#varintSizeInBytes(long)} to tell how many bytes.
   * @throws IllegalArgumentException if more than 64 bits were encoded
   */
  // included in the main api as this is used commonly, for example reading proto tags
  final int readVarint32() {
    byte b; // negative number implies MSB set
    if ((b = readByte()) >= 0) {
      return b;
    }
    int result = b & 0x7f;

    if ((b = readByte()) >= 0) {
      return result | b << 7;
    }
    result |= (b & 0x7f) << 7;

    if ((b = readByte()) >= 0) {
      return result | b << 14;
    }
    result |= (b & 0x7f) << 14;

    if ((b = readByte()) >= 0) {
      return result | b << 21;
    }
    result |= (b & 0x7f) << 21;

    b = readByte();
    if ((b & 0xf0) != 0) {
      throw new IllegalArgumentException("Greater than 32-bit varint at position " + (pos() - 1));
    }
    return result | b << 28;
  }

  final long readVarint64() {
    byte b; // negative number implies MSB set
    if ((b = readByte()) >= 0) {
      return b;
    }

    long result = b & 0x7f;
    for (int i = 1; b < 0 && i < 10; i++) {
      b = readByte();
      if (i == 9 && (b & 0xf0) != 0) {
        throw new IllegalArgumentException("Greater than 64-bit varint at position " + (pos() - 1));
      }
      result |= (long) (b & 0x7f) << (i * 7);
    }
    return result;
  }

  final void require(int byteCount) {
    if (this.available() < byteCount) {
      throw new IllegalArgumentException(
        "Truncated: length " + byteCount + " > bytes available " + this.available());
    }
  }

  int checkReadArguments(byte[] dst, int offset, int length) {
    if (dst == null) throw new NullPointerException();
    if (offset < 0 || length < 0 || length > dst.length - offset) {
      throw new IndexOutOfBoundsException();
    }
    return Math.min(available(), length);
  }
}
