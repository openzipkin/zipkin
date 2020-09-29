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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ReadBufferTest {
  @Test public void byteBuffer_limited() {
    ByteBuffer buf = ByteBuffer.wrap("glove".getBytes(UTF_8));
    buf.get();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buf.slice());
    assertThat(readBuffer.readUtf8(readBuffer.available()))
      .isEqualTo("love");
  }

  @Test public void byteBuffer_arrayOffset() {
    ByteBuffer buf = ByteBuffer.wrap("glove".getBytes(UTF_8), 1, 4);
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buf.slice());
    assertThat(readBuffer.pos()).isEqualTo(0);
    assertThat(readBuffer.available()).isEqualTo(4);
    assertThat(readBuffer.readUtf8(readBuffer.available()))
      .isEqualTo("love");
  }

  @Test public void readVarint32() {
    assertReadVarint32(0);
    assertReadVarint32(0b0011_1111_1111_1111);
    assertReadVarint32(0xFFFFFFFF);
  }

  static void assertReadVarint32(int value) {
    byte[] bytes = new byte[WriteBuffer.varintSizeInBytes(value)];
    WriteBuffer.wrap(bytes).writeVarint(value);

    assertThat(ReadBuffer.wrap(bytes).readVarint32())
      .isEqualTo(value);
  }

  @Test public void readShort_bytes() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readShort_byteBuff() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readShort_byteBuff_littleEndian() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readInt_bytes() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readInt_byteBuff() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readInt_byteBuff_littleEndian() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLong_bytes() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readLong())
      .isEqualTo(0x0102030405060708L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLong_byteBuff() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readLong())
      .isEqualTo(0x0102030405060708L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLong_byteBuff_littleEndian() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readLong())
      .isEqualTo(0x0102030405060708L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLongLe_bytes() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readLongLe())
      .isEqualTo(0x0807060504030201L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLongLe_byteBuff() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readLongLe())
      .isEqualTo(0x0807060504030201L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readLongLe_byteBuff_littleEndian() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readLongLe())
      .isEqualTo(0x0807060504030201L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test public void readVarint32_malformedTooBig() {
    byte[] bytes = new byte[8];
    WriteBuffer.wrap(bytes).writeLongLe(0xffffffffffffL);

    try {
      ReadBuffer.wrap(bytes).readVarint32();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e)
        .hasMessage("Greater than 32-bit varint at position 4");
    }
  }

  @Test public void readVarint64() {
    assertReadVarint64(0L);
    assertReadVarint64(0b0011_1111_1111_1111L);
    assertReadVarint64(0xffffffffffffffffL);
  }

  static void assertReadVarint64(long value) {
    byte[] bytes = new byte[WriteBuffer.varintSizeInBytes(value)];
    WriteBuffer.wrap(bytes).writeVarint(value);

    assertThat(ReadBuffer.wrap(bytes).readVarint64())
      .isEqualTo(value);
  }

  @Test public void readVarint64_malformedTooBig() {
    byte[] bytes = new byte[16];
    WriteBuffer buffer = WriteBuffer.wrap(bytes);
    buffer.writeLongLe(0xffffffffffffffffL);
    buffer.writeLongLe(0xffffffffffffffffL);

    try {
      ReadBuffer.wrap(bytes).readVarint64();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e)
        .hasMessage("Greater than 64-bit varint at position 9");
    }
  }
}
