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

import org.junit.Test;
import sun.net.util.IPAddressUtil;

import static org.assertj.core.api.Assertions.assertThat;
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
}
