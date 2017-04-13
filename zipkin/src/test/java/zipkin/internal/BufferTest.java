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
package zipkin.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import org.junit.Test;
import sun.net.util.IPAddressUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.Buffer.jsonEscapedSizeInBytes;
import static zipkin.internal.Util.UTF_8;

public class BufferTest {
  // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
  @Test public void utf8SizeInBytes() {
    for (int codepoint = 0; codepoint <= 0x10FFFF; codepoint++) {
      if (codepoint == 0xD800) codepoint = 0xDFFF + 1; // skip surrogates
      if (Character.isDefined(codepoint)) {
        String test = new String(Character.toChars(codepoint));
        int expected = test.getBytes(Util.UTF_8).length;
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
      String test = new String(new int[]{'a', codepoint, 'c'}, 0, 3);
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

  // Test borrowed from guava InetAddressesTest
  @Test public void ipv6() {
    assertThat(writeIpV6("1:2:3:4:5:6:7:8"))
        .isEqualTo("1:2:3:4:5:6:7:8");
    assertThat(writeIpV6("2001:0:0:4:0000:0:0:8"))
        .isEqualTo("2001:0:0:4::8");
    assertThat(writeIpV6("2001:0:0:4:5:6:7:8"))
        .isEqualTo("2001::4:5:6:7:8");
    assertThat(writeIpV6("2001:0:3:4:5:6:7:8"))
        .isEqualTo("2001::3:4:5:6:7:8");
    assertThat(writeIpV6("0:0:3:0:0:0:0:ffff"))
        .isEqualTo("0:0:3::ffff");
    assertThat(writeIpV6("0:0:0:4:0:0:0:ffff"))
        .isEqualTo("::4:0:0:0:ffff");
    assertThat(writeIpV6("0:0:0:0:5:0:0:ffff"))
        .isEqualTo("::5:0:0:ffff");
    assertThat(writeIpV6("1:0:0:4:0:0:7:8"))
        .isEqualTo("1::4:0:0:7:8");
    assertThat(writeIpV6("0:0:0:0:0:0:0:0"))
        .isEqualTo("::");
    assertThat(writeIpV6("0:0:0:0:0:0:0:1"))
        .isEqualTo("::1");
    assertThat(writeIpV6("2001:0658:022a:cafe::"))
        .isEqualTo("2001:658:22a:cafe::");
    assertThat(writeIpV6("::1.2.3.4"))
        .isEqualTo("::102:304");
  }

  static String writeIpV6(String address) {
    byte[] ipv6 = IPAddressUtil.textToNumericFormatV6(address);
    byte[] buffered = new Buffer(Buffer.ipv6SizeInBytes(ipv6)).writeIpV6(ipv6).toByteArray();
    return new String(buffered, UTF_8);
  }

  @Test
  public void asciiSizeInBytes_long() throws IOException {
    assertThat(asciiSizeInBytes(0L)).isEqualTo(1);
    assertThat(asciiSizeInBytes(-1005656679588439279L)).isEqualTo(20);
    assertThat(asciiSizeInBytes(-9223372036854775808L /* Long.MIN_VALUE */)).isEqualTo(20);
    assertThat(asciiSizeInBytes(123456789L)).isEqualTo(9);
  }

  @Test
  public void writeAscii_long() throws IOException {
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

  @Test
  public void jsonEscapedSizeInBytes_string() throws IOException {
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {0, 'a', 1})))
        .isEqualTo(13);
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {'"', '\\', '\t', '\b'})))
        .isEqualTo(8);
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {'\n', '\r', '\f'})))
        .isEqualTo(6);
    assertThat(jsonEscapedSizeInBytes("\u2028 and \u2029"))
        .isEqualTo(17);
    assertThat(jsonEscapedSizeInBytes("\"foo"))
        .isEqualTo(5);
  }

  @Test
  public void jsonEscapedSizeInBytes_bytes() throws IOException {
    assertThat(jsonEscapedSizeInBytes(new byte[] {0, 'a', 1}))
        .isEqualTo(13);
    assertThat(jsonEscapedSizeInBytes(new byte[] {'"', '\\', '\t', '\b'}))
        .isEqualTo(8);
    assertThat(jsonEscapedSizeInBytes(new byte[] {'\n', '\r', '\f'}))
        .isEqualTo(6);
    assertThat(jsonEscapedSizeInBytes("\u2028 and \u2029".getBytes(UTF_8)))
        .isEqualTo(17);
    assertThat(jsonEscapedSizeInBytes("\"foo".getBytes(UTF_8)))
        .isEqualTo(5);
  }

  @Test
  public void writeJsonEscaped_string() throws IOException {
    assertThat(writeJsonEscaped(new String(new char[] {0, 'a', 1})))
        .isEqualTo("\\u0000a\\u0001");
    assertThat(writeJsonEscaped(new String(new char[] {'"', '\\', '\t', '\b'})))
        .isEqualTo("\\\"\\\\\\t\\b");
    assertThat(writeJsonEscaped(new String(new char[] {'\n', '\r', '\f'})))
        .isEqualTo("\\n\\r\\f");
    assertThat(writeJsonEscaped("\u2028 and \u2029"))
        .isEqualTo("\\u2028 and \\u2029");
    assertThat(writeJsonEscaped("\"foo"))
        .isEqualTo("\\\"foo");
  }

  @Test
  public void writeJsonEscaped_bytes() throws IOException {
    assertThat(writeJsonEscaped(new byte[] {0, 'a', 1}))
        .isEqualTo("\\u0000a\\u0001");
    assertThat(writeJsonEscaped(new byte[] {'"', '\\', '\t', '\b'}))
        .isEqualTo("\\\"\\\\\\t\\b");
    assertThat(writeJsonEscaped(new byte[] {'\n', '\r', '\f'}))
        .isEqualTo("\\n\\r\\f");
    assertThat(writeJsonEscaped("\u2028 and \u2029".getBytes(UTF_8)))
        .isEqualTo("\\u2028 and \\u2029");
    assertThat(writeJsonEscaped("\"foo".getBytes(UTF_8)))
        .isEqualTo("\\\"foo");
  }

  static String writeJsonEscaped(String v) {
    byte[] buffered = new Buffer(jsonEscapedSizeInBytes(v)).writeJsonEscaped(v).toByteArray();
    return new String(buffered, UTF_8);
  }

  static String writeJsonEscaped(byte[] v) {
    byte[] buffered = new Buffer(jsonEscapedSizeInBytes(v)).writeJsonEscaped(v).toByteArray();
    return new String(buffered, UTF_8);
  }

  // Test creating Buffer for a long string
  @Test
  public void writeString() throws UnsupportedEncodingException {
    StringBuffer stringBuffer = new StringBuffer();
    for (int i = 0 ; i < 100000 ; i ++) {
      stringBuffer.append("a");
    }
    String string = stringBuffer.toString();
    byte[] buffered = new Buffer(Buffer.asciiSizeInBytes(string)).writeAscii(string).toByteArray();
    assertThat(new String(buffered, "US-ASCII")).isEqualTo(string);
  }
}
