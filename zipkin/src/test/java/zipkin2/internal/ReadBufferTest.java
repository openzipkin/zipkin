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

import java.nio.ByteBuffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin2.TestObjects.UTF_8;

public class ReadBufferTest {
  @Test public void byteBuffer_limited() {
    ByteBuffer buf = ByteBuffer.wrap("glove".getBytes(UTF_8));
    buf.get();
    ReadBuffer readBuffer = ReadBuffer.wrapUnsafe(buf.slice());
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
