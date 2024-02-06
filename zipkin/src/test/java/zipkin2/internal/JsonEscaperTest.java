/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
