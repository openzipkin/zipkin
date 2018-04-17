/**
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
package zipkin2.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.UTF_8;

public class BufferTest {
  // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
  @Test public void utf8SizeInBytes() {
    for (int codepoint = 0; codepoint <= 0x10FFFF; codepoint++) {
      if (codepoint == 0xD800) codepoint = 0xDFFF + 1; // skip surrogates
      if (Character.isDefined(codepoint)) {
        String test = new String(Character.toChars(codepoint));
        int expected = test.getBytes(UTF_8).length;
        int actual = Buffer.utf8SizeInBytes(test);
        if (actual != expected) {
          throw new AssertionError(actual + " length != " + expected + " for " + codepoint);
        }
      }
    }
  }

  /** Uses test data and codepoint wrapping trick from okhttp3.FormBodyTest */
  @Test public void utf8_malformed() {
    for (int codepoint : Arrays.asList(0xD800, 0xDFFF, 0xD83D)) {
      String test = new String(new int[] {'a', codepoint, 'c'}, 0, 3);
      assertThat(Buffer.utf8SizeInBytes(test))
        .isEqualTo(3);
      assertThat(new Buffer(3).writeUtf8(test).toByteArray())
        .containsExactly('a', '?', 'c');
    }
  }

  @Test public void utf8_21Bit_truncated() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array, 0, array.length - 1);
    assertThat(Buffer.utf8SizeInBytes(test))
      .isEqualTo(5);

    byte[] out = new Buffer(5).writeUtf8(test).toByteArray();
    assertThat(new String(out, UTF_8))
      .isEqualTo("\uD83C\uDC00?");
  }

  @Test public void utf8_21Bit_brokenLowSurrogate() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array);
    assertThat(Buffer.utf8SizeInBytes(test))
      .isEqualTo(6);

    byte[] out = new Buffer(6).writeUtf8(test).toByteArray();
    assertThat(new String(out, UTF_8))
      .isEqualTo("\uD83C\uDC00?c");
  }

  @Test public void utf8_matchesJRE() {
    // examples from http://utf8everywhere.org/
    for (String string : Arrays.asList(
      "Приве́т नमस्ते שָׁלוֹם",
      "ю́ cyrillic small letter yu with acute",
      "∃y ∀x ¬(x ≺ y)"
    )) {
      int encodedSize = Buffer.utf8SizeInBytes(string);
      assertThat(encodedSize)
        .isEqualTo(string.getBytes(UTF_8).length);

      Buffer bufferUtf8 = new Buffer(encodedSize);
      bufferUtf8.writeUtf8(string);
      assertThat(new String(bufferUtf8.toByteArray(), UTF_8))
        .isEqualTo(string);
    }
  }

  @Test public void utf8_matchesAscii() throws Exception {
    String ascii = "86154a4ba6e913854d1e00c0db9010db";
    int encodedSize = Buffer.utf8SizeInBytes(ascii);
    assertThat(encodedSize)
      .isEqualTo(ascii.length());

    Buffer bufferAscii = new Buffer(encodedSize);
    bufferAscii.writeAscii(ascii);
    assertThat(new String(bufferAscii.toByteArray(), "US-ASCII"))
      .isEqualTo(ascii);

    Buffer bufferUtf8 = new Buffer(encodedSize);
    bufferUtf8.writeUtf8(ascii);
    assertThat(new String(bufferUtf8.toByteArray(), "US-ASCII"))
      .isEqualTo(ascii);
  }

  @Test public void emoji() {
    byte[] emojiBytes = {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x81};
    String emoji = new String(emojiBytes, UTF_8);
    assertThat(Buffer.utf8SizeInBytes(emoji))
      .isEqualTo(emojiBytes.length);
    assertThat(new Buffer(emojiBytes.length).writeUtf8(emoji).toByteArray())
      .isEqualTo(emojiBytes);
  }

  @Test public void writeAscii_long() throws IOException {
    assertThat(writeAscii(-1005656679588439279L))
      .isEqualTo("-1005656679588439279");
    assertThat(writeAscii(0L))
      .isEqualTo("0");
    assertThat(writeAscii(-9223372036854775808L /* Long.MIN_VALUE */))
      .isEqualTo("-9223372036854775808");
    assertThat(writeAscii(123456789L))
      .isEqualTo("123456789");
  }

  static String writeAscii(long v) {
    byte[] buffered = new Buffer(Buffer.asciiSizeInBytes(v)).writeAscii(v).toByteArray();
    return new String(buffered, UTF_8);
  }

  // Test creating Buffer for a long string
  @Test public void writeString() throws UnsupportedEncodingException {
    StringBuffer stringBuffer = new StringBuffer();
    for (int i = 0; i < 100000; i++) {
      stringBuffer.append("a");
    }
    String string = stringBuffer.toString();
    byte[] buffered = new Buffer(string.length()).writeAscii(string).toByteArray();
    assertThat(new String(buffered, "US-ASCII")).isEqualTo(string);
  }

  @Test public void unsignedVarintSize_32_largest() {
    // largest to encode is a negative number
    assertThat(Buffer.varintSizeInBytes(Integer.MIN_VALUE))
      .isEqualTo(5);
  }

  @Test public void unsignedVarintSize_64_largest() {
    // largest to encode is a negative number
    assertThat(Buffer.varintSizeInBytes(Long.MIN_VALUE))
      .isEqualTo(10);
  }

  @Test public void writeLongLe_matchesByteBuffer() {
    for (long number : Arrays.asList(Long.MIN_VALUE, 0L, Long.MAX_VALUE)) {
      Buffer buffer = new Buffer(8);
      buffer.writeLongLe(number);

      ByteBuffer byteBuffer = ByteBuffer.allocate(8);
      byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
      byteBuffer.putLong(number);

      assertThat(buffer.toByteArray())
        .containsExactly(byteBuffer.array());
    }
  }

  // https://developers.google.com/protocol-buffers/docs/encoding#varints
  @Test public void writeVarint_32() {
    int number = 300;

    Buffer buffer = new Buffer(Buffer.varintSizeInBytes(number));
    buffer.writeVarint(number);

    assertThat(buffer.toByteArray())
      .containsExactly(0b1010_1100, 0b0000_0010);
  }

  // https://developers.google.com/protocol-buffers/docs/encoding#varints
  @Test public void writeVarint_64() {
    long number = 300;

    Buffer buffer = new Buffer(Buffer.varintSizeInBytes(number));
    buffer.writeVarint(number);

    assertThat(buffer.toByteArray())
      .containsExactly(0b1010_1100, 0b0000_0010);
  }

  @Test public void writeVarint_ports() {
    // normal case
    Buffer buffer = new Buffer(Buffer.varintSizeInBytes(80));
    buffer.writeVarint(80);

    assertThat(buffer.toByteArray())
      .containsExactly(0b0101_0000);

    // largest value to not require more than 2 bytes (14 bits set)
    buffer = new Buffer(Buffer.varintSizeInBytes(16383));
    buffer.writeVarint(16383);

    assertThat(buffer.toByteArray())
      .containsExactly(0b1111_1111, 0b0111_1111);

    // worst case is a byte longer than fixed 16
    buffer = new Buffer(Buffer.varintSizeInBytes(65535));
    buffer.writeVarint(65535);

    assertThat(buffer.toByteArray())
      .containsExactly(0b1111_1111, 0b1111_1111, 0b0000_0011);
  }
}
