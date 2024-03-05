/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

class ReadBufferTest {
  @Test void byteBuffer_limited() {
    ByteBuffer buf = ByteBuffer.wrap("glove".getBytes(UTF_8));
    buf.get();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buf.slice());
    assertThat(readBuffer.readUtf8(readBuffer.available()))
      .isEqualTo("love");
  }

  @Test void byteBuffer_arrayOffset() {
    ByteBuffer buf = ByteBuffer.wrap("glove".getBytes(UTF_8), 1, 4);
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buf.slice());
    assertThat(readBuffer.pos()).isEqualTo(0);
    assertThat(readBuffer.available()).isEqualTo(4);
    assertThat(readBuffer.readUtf8(readBuffer.available()))
      .isEqualTo("love");
  }

  @Test void readVarint32() {
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

  @Test void readShort_bytes() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readShort_byteBuff() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readShort_byteBuff_littleEndian() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readShort()).isEqualTo((short) 0x0102);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readInt_bytes() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readInt_byteBuff() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readInt_byteBuff_littleEndian() {
    byte[] bytes = {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buffer);
    assertThat(readBuffer).isInstanceOf(ReadBuffer.Buff.class);

    assertThat(readBuffer.readInt()).isEqualTo(0x01020304);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readLong_bytes() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readLong())
      .isEqualTo(0x0102030405060708L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readLong_byteBuff() {
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

  @Test void readLong_byteBuff_littleEndian() {
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

  @Test void readLongLe_bytes() {
    byte[] bytes = {
      (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
      (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    };

    ReadBuffer readBuffer = ReadBuffer.wrap(bytes);

    assertThat(readBuffer.readLongLe())
      .isEqualTo(0x0807060504030201L);
    assertThat(readBuffer.available()).isZero();
  }

  @Test void readLongLe_byteBuff() {
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

  @Test void readLongLe_byteBuff_littleEndian() {
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

  @Test void readVarint32_malformedTooBig() {
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

  @Test void readVarint64() {
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

  @Test void readVarint64_malformedTooBig() {
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
