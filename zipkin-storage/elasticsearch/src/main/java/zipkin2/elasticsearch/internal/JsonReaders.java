/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.internal.Nullable;

/**
 * Utilities used here aim to reduce allocation overhead for common requests. It does so by skipping
 * unrelated fields. This is used for responses which could be large.
 */
public final class JsonReaders {
  /**
   * Navigates to a field of a JSON-serialized object. For example,
   *
   * <pre>{@code
   * JsonParser status = enterPath(JsonAdapters.jsonParser(stream), "message", "status");
   * if (status != null) throw new IllegalStateException(status.nextString());
   * }</pre>
   */
  @Nullable public static JsonParser enterPath(JsonParser parser, String path1, String path2)
    throws IOException {
    return enterPath(parser, path1) != null ? enterPath(parser, path2) : null;
  }

  @Nullable public static JsonParser enterPath(JsonParser parser, String path) throws IOException {
    if (!checkStartObject(parser, false)) return null;

    JsonToken value;
    while ((value = parser.nextValue()) != JsonToken.END_OBJECT) {
      if (value == null) {
        // End of input so ignore.
        return null;
      }
      if (parser.getCurrentName().equalsIgnoreCase(path) && value != JsonToken.VALUE_NULL) {
        return parser;
      } else {
        parser.skipChildren();
      }
    }
    return null;
  }

  public static List<String> collectValuesNamed(JsonParser parser, String name) throws IOException {
    checkStartObject(parser, true);
    Set<String> result = new LinkedHashSet<>();
    visitObject(parser, name, result);
    return new ArrayList<>(result);
  }

  static void visitObject(JsonParser parser, String name, Set<String> result) throws IOException {
    checkStartObject(parser, true);
    JsonToken value;
    while ((value = parser.nextValue()) != JsonToken.END_OBJECT) {
      if (value == null) {
        // End of input so ignore.
        return;
      }
      if (parser.getCurrentName().equals(name)) {
        result.add(parser.getText());
      } else {
        visitNextOrSkip(parser, name, result);
      }
    }
  }

  static void visitNextOrSkip(JsonParser parser, String name, Set<String> result)
    throws IOException {
    switch (parser.currentToken()) {
      case START_ARRAY:
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
          if (token == null) {
            // End of input so ignore.
            return;
          }
          visitObject(parser, name, result);
        }
        break;
      case START_OBJECT:
        visitObject(parser, name, result);
        break;
      default:
        // Skip current value.
    }
  }

  static boolean checkStartObject(JsonParser parser, boolean shouldThrow) throws IOException {
    try {
      JsonToken currentToken = parser.currentToken();
      // The parser may not be at a token, yet. If that's the case advance.
      if (currentToken == null) currentToken = parser.nextToken();

      // If we are still not at the expected token, we could be an another or an empty body.
      if (currentToken == JsonToken.START_OBJECT) return true;
      if (shouldThrow) {
        throw new IllegalArgumentException("Expected start object, was " + currentToken);
      }
      return false;
    } catch (Throwable e) { // likely not json
      if (shouldThrow) throw e;
      return false;
    }
  }

  JsonReaders() {
  }
}
