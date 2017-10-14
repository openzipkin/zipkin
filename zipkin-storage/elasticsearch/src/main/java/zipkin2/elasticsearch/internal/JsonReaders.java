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
package zipkin2.elasticsearch.internal;

import com.squareup.moshi.JsonReader;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.internal.Nullable;

public final class JsonReaders {

  /**
   * This saves you from having to define nested types to read a single value
   *
   * <p>Instead of defining two types like this, and double-checking null..
   * <pre>{@code
   * class Response {
   *   Message message;
   * }
   * class Message {
   *   String status;
   * }
   * JsonAdapter<Response> adapter = moshi.adapter(Response.class);
   * Message message = adapter.fromJson(body.source());
   * if (message != null && message.status != null) throw new IllegalStateException(message.status);
   * }</pre>
   *
   * <p>You can advance to the field directly.
   * <pre>{@code
   * JsonReader status = enterPath(JsonReader.of(body.source()), "message", "status");
   * if (status != null) throw new IllegalStateException(status.nextString());
   * }</pre>
   */
  @Nullable
  public static JsonReader enterPath(JsonReader reader, String path1, String path2)
      throws IOException {
    return enterPath(reader, path1) != null ? enterPath(reader, path2) : null;
  }

  @Nullable
  public static JsonReader enterPath(JsonReader reader, String path) throws IOException {
    try {
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null;
    } catch (EOFException e) {
      return null;
    }
    reader.beginObject();
    while (reader.hasNext()) {
      if (reader.nextName().equals(path) && reader.peek() != JsonReader.Token.NULL) {
        return reader;
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    return null;
  }

  public static List<String> collectValuesNamed(JsonReader reader, String name) throws IOException {
    Set<String> result = new LinkedHashSet<>();
    visitObject(reader, name, result);
    return new ArrayList<>(result);
  }

  static void visitObject(JsonReader reader, String name, Set<String> result) throws IOException {
    reader.beginObject();
    while (reader.hasNext()) {
      if (reader.nextName().equals(name)) {
        result.add(reader.nextString());
      } else {
        visitNextOrSkip(reader, name, result);
      }
    }
    reader.endObject();
  }

  static void visitNextOrSkip(JsonReader reader, String name, Set<String> result)
      throws IOException {
    switch (reader.peek()) {
      case BEGIN_ARRAY:
        reader.beginArray();
        while (reader.hasNext()) visitObject(reader, name, result);
        reader.endArray();
        break;
      case BEGIN_OBJECT:
        visitObject(reader, name, result);
        break;
      default:
        reader.skipValue();
    }
  }

  JsonReaders() {
  }
}
