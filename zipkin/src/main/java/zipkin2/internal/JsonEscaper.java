/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
  public static CharSequence jsonEscape(CharSequence v) {
    int length = v.length();
    if (length == 0) return v;

    int afterReplacement = 0;
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
        if (builder == null) builder = new StringBuilder(length);
        builder.append(v, afterReplacement, i);
      }
      if (builder == null) builder = new StringBuilder(length);
      builder.append(replacement);
      afterReplacement = i + 1;
    }
    if (builder == null) return v; // then we didn't escape anything

    if (afterReplacement < length) {
      builder.append(v, afterReplacement, length);
    }
    return builder;
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

  public static int jsonEscapedSizeInBytes(CharSequence v) {
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
    return WriteBuffer.utf8SizeInBytes(v) + escapingOverhead;
  }
}
