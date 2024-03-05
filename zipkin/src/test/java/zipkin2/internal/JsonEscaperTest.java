/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;

class JsonEscaperTest {

  @Test void testJsonEscapedSizeInBytes() {
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

  @Test void testJsonEscape() {
    assertThat(jsonEscape(new String(new char[] {0, 'a', 1})).toString())
      .isEqualTo("\\u0000a\\u0001");
    assertThat(jsonEscape(new String(new char[] {'"', '\\', '\t', '\b'})).toString())
      .isEqualTo("\\\"\\\\\\t\\b");
    assertThat(jsonEscape(new String(new char[] {'\n', '\r', '\f'})).toString())
      .isEqualTo("\\n\\r\\f");
    assertThat(jsonEscape("\u2028 and \u2029").toString())
      .isEqualTo("\\u2028 and \\u2029");
    assertThat(jsonEscape("\"foo").toString())
      .isEqualTo("\\\"foo");
  }
}
