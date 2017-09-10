/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
}
