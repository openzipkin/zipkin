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
import java.util.Arrays;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class WriteBufferTest {
  // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
  @Test public void utf8SizeInBytes() {
    for (int codepoint = 0; codepoint <= 0x10FFFF; codepoint++) {
      if (codepoint == 0xD800) codepoint = 0xDFFF + 1; // skip surrogates
      if (Character.isDefined(codepoint)) {
        String test = new String(Character.toChars(codepoint));
        int expected = test.getBytes(UTF_8).length;
        int actual = WriteBuffer.utf8SizeInBytes(test);
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
      assertThat(WriteBuffer.utf8SizeInBytes(test))
        .isEqualTo(3);

      byte[] bytes = new byte[3];
      WriteBuffer.wrap(bytes).writeUtf8(test);
      assertThat(bytes)
        .containsExactly('a', '?', 'c');
    }
  }

  @Test public void utf8_21Bit_truncated() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array, 0, array.length - 1);
    assertThat(WriteBuffer.utf8SizeInBytes(test))
      .isEqualTo(5);

    byte[] bytes = new byte[5];
    WriteBuffer.wrap(bytes).writeUtf8(test);
    assertThat(new String(bytes, UTF_8))
      .isEqualTo("\uD83C\uDC00?");
  }

  @Test public void utf8_21Bit_brokenLowSurrogate() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array);
    assertThat(WriteBuffer.utf8SizeInBytes(test))
      .isEqualTo(6);

    byte[] bytes = new byte[6];
    WriteBuffer.wrap(bytes).writeUtf8(test);
    assertThat(new String(bytes, UTF_8))
      .isEqualTo("\uD83C\uDC00?c");
  }

  @Test public void utf8_matchesJRE() {
    // examples from http://utf8everywhere.org/
    for (String string : Arrays.asList(
      "Приве́т नमस्ते שָׁלוֹם",
      "ю́ cyrillic small letter yu with acute",
      "∃y ∀x ¬(x ≺ y)"
    )) {
      int encodedSize = WriteBuffer.utf8SizeInBytes(string);
      assertThat(encodedSize)
        .isEqualTo(string.getBytes(UTF_8).length);

      byte[] bytes = new byte[encodedSize];
      WriteBuffer.wrap(bytes).writeUtf8(string);
      assertThat(new String(bytes, UTF_8))
        .isEqualTo(string);
    }
  }

  @Test public void utf8_matchesAscii() {
    String ascii = "86154a4ba6e913854d1e00c0db9010db";
    int encodedSize = WriteBuffer.utf8SizeInBytes(ascii);
    assertThat(encodedSize)
      .isEqualTo(ascii.length());

    byte[] bytes = new byte[encodedSize];
    WriteBuffer.wrap(bytes).writeAscii(ascii);
    assertThat(new String(bytes, UTF_8))
      .isEqualTo(ascii);

    WriteBuffer.wrap(bytes).writeUtf8(ascii);
    assertThat(new String(bytes, UTF_8))
      .isEqualTo(ascii);
  }

  @Test public void emoji() {
    byte[] emojiBytes = {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x81};
    String emoji = new String(emojiBytes, UTF_8);
    assertThat(WriteBuffer.utf8SizeInBytes(emoji))
      .isEqualTo(emojiBytes.length);

    byte[] bytes = new byte[emojiBytes.length];
    WriteBuffer.wrap(bytes).writeUtf8(emoji);
    assertThat(bytes)
      .isEqualTo(emojiBytes);
  }

  @Test public void writeAscii_long() {
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
    byte[] bytes = new byte[WriteBuffer.asciiSizeInBytes(v)];
    WriteBuffer.wrap(bytes).writeAscii(v);
    return new String(bytes, UTF_8);
  }

  // Test creating Buffer for a long string
  @Test public void writeString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 100000; i++) {
      builder.append("a");
    }
    String string = builder.toString();
    byte[] bytes = new byte[string.length()];
    WriteBuffer.wrap(bytes).writeAscii(string);
    assertThat(new String(bytes, UTF_8)).isEqualTo(string);
  }

  @Test public void unsignedVarintSize_32_largest() {
    // largest to encode is a negative number
    assertThat(WriteBuffer.varintSizeInBytes(Integer.MIN_VALUE))
      .isEqualTo(5);
  }

  @Test public void unsignedVarintSize_64_largest() {
    // largest to encode is a negative number
    assertThat(WriteBuffer.varintSizeInBytes(Long.MIN_VALUE))
      .isEqualTo(10);
  }

  @Test public void writeLongLe_matchesByteBuffer() {
    for (long number : Arrays.asList(Long.MIN_VALUE, 0L, Long.MAX_VALUE)) {
      byte[] bytes = new byte[8];
      WriteBuffer.wrap(bytes).writeLongLe(number);

      ByteBuffer byteBuffer = ByteBuffer.allocate(8);
      byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
      byteBuffer.putLong(number);

      assertThat(bytes)
        .containsExactly(byteBuffer.array());
    }
  }

  // https://developers.google.com/protocol-buffers/docs/encoding#varints
  @Test public void writeVarint_32() {
    int number = 300;

    byte[] bytes = new byte[WriteBuffer.varintSizeInBytes(number)];
    WriteBuffer.wrap(bytes).writeVarint(number);

    assertThat(bytes)
      .containsExactly(0b1010_1100, 0b0000_0010);
  }

  // https://developers.google.com/protocol-buffers/docs/encoding#varints
  @Test public void writeVarint_64() {
    long number = 300;

    byte[] bytes = new byte[WriteBuffer.varintSizeInBytes(number)];
    WriteBuffer.wrap(bytes).writeVarint(number);

    assertThat(bytes)
      .containsExactly(0b1010_1100, 0b0000_0010);
  }

  @Test public void writeVarint_ports() {
    // normal case
    byte[] bytes = new byte[WriteBuffer.varintSizeInBytes(80)];
    WriteBuffer.wrap(bytes).writeVarint(80);

    assertThat(bytes)
      .containsExactly(0b0101_0000);

    // largest value to not require more than 2 bytes (14 bits set)
    bytes = new byte[WriteBuffer.varintSizeInBytes(16383)];
    WriteBuffer.wrap(bytes).writeVarint(16383);

    assertThat(bytes)
      .containsExactly(0b1111_1111, 0b0111_1111);

    // worst case is a byte longer than fixed 16
    bytes = new byte[WriteBuffer.varintSizeInBytes(65535)];
    WriteBuffer.wrap(bytes).writeVarint(65535);

    assertThat(bytes)
      .containsExactly(0b1111_1111, 0b1111_1111, 0b0000_0011);

    // most bits
    bytes = new byte[WriteBuffer.varintSizeInBytes(0xFFFFFFFF)];
    WriteBuffer.wrap(bytes).writeVarint(0xFFFFFFFF);

    // we have a total of 32 bits encoded
    assertThat(bytes)
      .containsExactly(0b1111_1111, 0b1111_1111, 0b1111_1111, 0b1111_1111, 0b0000_1111);
  }
}
