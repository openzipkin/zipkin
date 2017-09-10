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

public final class JsonEscaper {
  /** Exposed for ElasticSearch HttpBulkIndexer */
  public static String jsonEscape(String v) {
    if (v.isEmpty()) return v;
    int afterReplacement = 0;
    int length = v.length();
    StringBuilder builder = null;
    for (int i = 0; i < length; i++) {
      char c = v.charAt(i);
      String replacement;
      if (c < 0x80) {
        replacement = REPLACEMENT_CHARS[c];
        if (replacement == null) continue;
      } else if (c == '\u2028') {
        replacement = U2028;
      } else if (c == '\u2029') {
        replacement = U2029;
      } else {
        continue;
      }
      if (afterReplacement < i) { // write characters between the last replacement and now
        if (builder == null) builder = new StringBuilder();
        builder.append(v, afterReplacement, i);
      }
      if (builder == null) builder = new StringBuilder();
      builder.append(replacement);
      afterReplacement = i + 1;
    }
    String escaped;
    if (builder == null) { // then we didn't escape anything
      escaped = v;
    } else {
      if (afterReplacement < length) {
        builder.append(v, afterReplacement, length);
      }
      escaped = builder.toString();
    }
    return escaped;
  }

  public static boolean needsJsonEscaping(byte[] v) {
    for (int i = 0; i < v.length; i++) {
      int current = v[i] & 0xFF;
      if (i >= 2 &&
        // Is this the end of a u2028 or u2028 UTF-8 codepoint?
        // 0xE2 0x80 0xA8 == u2028; 0xE2 0x80 0xA9 == u2028
        (current == 0xA8 || current == 0xA9)
        && (v[i - 1] & 0xFF) == 0x80
        && (v[i - 2] & 0xFF) == 0xE2) {
        return true;
      } else if (current < 0x80 && REPLACEMENT_CHARS[current] != null) {
        return true;
      }
    }
    return false; // must be a string we don't need to escape.
  }

  /*
   * Escaping logic adapted from Moshi, which we couldn't use due to language level
   *
   * From RFC 7159, "All Unicode characters may be placed within the
   * quotation marks except for the characters that must be escaped:
   * quotation mark, reverse solidus, and the control characters
   * (U+0000 through U+001F)."
   *
   * We also escape '\u2028' and '\u2029', which JavaScript interprets as
   * newline characters. This prevents eval() from failing with a syntax
   * error. http://code.google.com/p/google-gson/issues/detail?id=341
   */
  private static final String[] REPLACEMENT_CHARS;

  static {
    REPLACEMENT_CHARS = new String[128];
    for (int i = 0; i <= 0x1f; i++) {
      REPLACEMENT_CHARS[i] = String.format("\\u%04x", (int) i);
    }
    REPLACEMENT_CHARS['"'] = "\\\"";
    REPLACEMENT_CHARS['\\'] = "\\\\";
    REPLACEMENT_CHARS['\t'] = "\\t";
    REPLACEMENT_CHARS['\b'] = "\\b";
    REPLACEMENT_CHARS['\n'] = "\\n";
    REPLACEMENT_CHARS['\r'] = "\\r";
    REPLACEMENT_CHARS['\f'] = "\\f";
  }

  private static final String U2028 = "\\u2028";
  private static final String U2029 = "\\u2029";

  public static int jsonEscapedSizeInBytes(String v) {
    boolean ascii = true;
    int escapingOverhead = 0;
    for (int i = 0, length = v.length(); i < length; i++) {
      char c = v.charAt(i);
      if (c == '\u2028' || c == '\u2029') {
        escapingOverhead += 5;
      } else if (c >= 0x80) {
        ascii = false;
      } else {
        String maybeReplacement = REPLACEMENT_CHARS[c];
        if (maybeReplacement != null) escapingOverhead += maybeReplacement.length() - 1;
      }
    }
    if (ascii) return v.length() + escapingOverhead;
    return Buffer.utf8SizeInBytes(v) + escapingOverhead;
  }
}
