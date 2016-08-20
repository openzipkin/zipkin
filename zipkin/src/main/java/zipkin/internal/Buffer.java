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

import java.io.OutputStream;
import java.util.Arrays;

/** Similar to {@link java.io.ByteArrayInputStream}, except specialized and unsynchronized */
final class Buffer extends OutputStream {
  static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
  private byte[] buf;
  private int pos;

  Buffer() {
    this(128);
  }

  Buffer(int initialLength) {
    buf = new byte[initialLength];
  }

  @Override public void write(int v) {
    ensureCapacity(pos + 1);
    buf[pos++] = (byte) v;
  }

  @Override public void write(byte[] v) {
    ensureCapacity(pos + v.length);
    System.arraycopy(v, 0, buf, pos, v.length);
    pos += v.length;
  }

  void writeShort(int v) {
    ensureCapacity(pos + 2);
    write((v >>> 8L) & 0xff);
    write(v & 0xff);
  }

  void writeInt(int v) {
    ensureCapacity(pos + 4);
    buf[pos++] = (byte) ((v >>> 24L) & 0xff);
    buf[pos++] = (byte) ((v >>> 16L) & 0xff);
    buf[pos++] = (byte) ((v >>> 8L) & 0xff);
    buf[pos++] = (byte) (v & 0xff);
  }

  void writeLong(long v) {
    ensureCapacity(pos + 8);
    buf[pos++] = (byte) ((v >>> 56L) & 0xff);
    buf[pos++] = (byte) ((v >>> 48L) & 0xff);
    buf[pos++] = (byte) ((v >>> 40L) & 0xff);
    buf[pos++] = (byte) ((v >>> 32L) & 0xff);
    buf[pos++] = (byte) ((v >>> 24L) & 0xff);
    buf[pos++] = (byte) ((v >>> 16L) & 0xff);
    buf[pos++] = (byte) ((v >>> 8L) & 0xff);
    buf[pos++] = (byte) (v & 0xff);
  }

  /** Writes a length-prefixed string */
  void writeUtf8(String v) {
    byte[] temp = v.getBytes(Util.UTF_8);
    writeInt(temp.length);
    write(temp);
  }

  /** Shares the internal buffer, if it is fully written */
  byte[] toByteArray() {
    return pos == buf.length ? buf : Arrays.copyOf(buf, pos);
  }

  /** Doubles up to {@link #MAX_ARRAY_LENGTH} if necessary */
  private void ensureCapacity(int length) {
    if (length <= buf.length) return;
    if (length >= MAX_ARRAY_LENGTH) throw new OutOfMemoryError();

    int newLength = buf.length;
    while (newLength < length) {
      newLength = newLength * 2;
      if (newLength > Integer.MAX_VALUE) {
        newLength = MAX_ARRAY_LENGTH;
      }
    }

    buf = Arrays.copyOf(buf, newLength);
  }
}
