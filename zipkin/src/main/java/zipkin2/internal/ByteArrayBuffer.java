/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.internal;

final class ByteArrayBuffer extends Buffer {

  private final byte[] buf;
  int pos; // visible for testing

  ByteArrayBuffer(int size) {
    buf = new byte[size];
  }

  ByteArrayBuffer(byte[] buf, int pos) {
    this.buf = buf;
    this.pos = pos;
  }

  @Override public void writeByte(int v) {
    buf[pos++] = (byte) (v & 0xff);
  }

  @Override public void write(byte[] v) {
    System.arraycopy(v, 0, buf, pos, v.length);
    pos += v.length;
  }

  @Override void writeBackwards(long v) {
    int pos = this.pos += asciiSizeInBytes(v); // We write backwards from right to left.
    while (v != 0) {
      int digit = (int) (v % 10);
      buf[--pos] = DIGITS[digit];
      v /= 10;
    }
  }

  /** Inspired by {@code okio.Buffer.writeLong} */
  @Override public void writeLongHex(long v) {
    writeHexByte(buf, pos + 0, (byte) ((v >>> 56L) & 0xff));
    writeHexByte(buf, pos + 2, (byte) ((v >>> 48L) & 0xff));
    writeHexByte(buf, pos + 4, (byte) ((v >>> 40L) & 0xff));
    writeHexByte(buf, pos + 6, (byte) ((v >>> 32L) & 0xff));
    writeHexByte(buf, pos + 8, (byte) ((v >>> 24L) & 0xff));
    writeHexByte(buf, pos + 10, (byte) ((v >>> 16L) & 0xff));
    writeHexByte(buf, pos + 12, (byte) ((v >>> 8L) & 0xff));
    writeHexByte(buf, pos + 14, (byte) (v & 0xff));
    pos += 16;
  }

  @Override public void reset() {
    pos = 0;
  }

  @Override byte readByteUnsafe() {
    return buf[pos++];
  }

  @Override byte[] readByteArray(int length) {
    ensureLength(this, length);
    byte[] result = new byte[length];
    System.arraycopy(buf, pos, result, 0, length);
    pos += length;
    return result;
  }

  @Override int remaining() {
    return buf.length - pos;
  }

  @Override boolean skip(int maxCount) {
    int nextPos = pos + maxCount;
    if (nextPos > buf.length) {
      pos = buf.length;
      return false;
    }
    pos = nextPos;
    return true;
  }

  @Override public int pos() {
    return pos;
  }

  @Override public byte[] toByteArray() {
    // assert pos == buf.length;
    return buf;
  }
}
