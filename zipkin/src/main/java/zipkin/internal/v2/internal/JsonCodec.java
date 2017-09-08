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
package zipkin.internal.v2.internal;

import com.google.gson.stream.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.lang.String.format;

/**
 * This explicitly constructs instances of model classes via manual parsing for a number of
 * reasons.
 *
 * <ul> <li>Eliminates the need to keep separate model classes for thrift vs json</li> <li>Avoids
 * magic field initialization which, can miss constructor guards</li> <li>Allows us to safely re-use
 * the json form in toString methods</li> <li>Encourages logic to be based on the thrift shape of
 * objects</li> <li>Ensures the order and naming of the fields in json is stable</li> </ul>
 *
 * <p> There is the up-front cost of creating this, and maintenance of this to consider. However,
 * this should be easy to justify as these objects don't change much at all.
 */
public final class JsonCodec {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  public interface JsonReaderAdapter<T> {
    T fromJson(JsonReader reader) throws IOException;
  }

  public static <T> T read(JsonReaderAdapter<T> adapter, byte[] bytes) {
    if (bytes.length == 0) throw new IllegalArgumentException("Empty input reading " + adapter);
    try {
      return adapter.fromJson(jsonReader(bytes));
    } catch (Exception e) {
      throw exceptionReading(adapter.toString(), bytes, e);
    }
  }

  public static <T> List<T> readList(JsonReaderAdapter<T> adapter, byte[] bytes) {
    if (bytes.length == 0) {
      throw new IllegalArgumentException("Empty input reading List<" + adapter + ">");
    }
    JsonReader reader = jsonReader(bytes);
    List<T> result;
    try {
      reader.beginArray();
      result = reader.hasNext() ? new LinkedList<>() : Collections.emptyList();
      while (reader.hasNext()) result.add(adapter.fromJson(reader));
      reader.endArray();
      return result;
    } catch (Exception e) {
      throw exceptionReading("List<" + adapter + ">", bytes, e);
    }
  }

  static JsonReader jsonReader(byte[] bytes) {
    return new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8));
  }

  static <T> int sizeInBytes(Buffer.Writer<T> writer, List<T> value) {
    int length = value.size();
    int sizeInBytes = 2; // []
    if (length > 1) sizeInBytes += length - 1; // comma to join elements
    for (int i = 0; i < length; i++) {
      sizeInBytes += writer.sizeInBytes(value.get(i));
    }
    return sizeInBytes;
  }

  /** Inability to encode is a programming bug. */
  public static <T> byte[] write(Buffer.Writer<T> writer, T value) {
    Buffer b = new Buffer(writer.sizeInBytes(value));
    try {
      writer.write(value, b);
    } catch (RuntimeException e) {
      byte[] bytes = b.toByteArray();
      int lengthWritten = bytes.length;
      for (int i = 0; i < bytes.length; i++) {
        if (bytes[i] == 0) {
          lengthWritten = i;
          break;
        }
      }

      final byte[] bytesWritten;
      if (lengthWritten == bytes.length) {
        bytesWritten = bytes;
      } else {
        bytesWritten = new byte[lengthWritten];
        System.arraycopy(bytes, 0, bytesWritten, 0, lengthWritten);
      }

      String written = new String(bytesWritten, UTF_8);
      // Don't use value directly in the message, as its toString might be implemented using this
      // method. If that's the case, we'd stack overflow. Instead, emit what we've written so far.
      String message = format(
        "Bug found using %s to write %s as json. Wrote %s/%s bytes: %s",
        writer.getClass().getSimpleName().replace("AutoValue_", ""),
        value.getClass().getSimpleName(), lengthWritten, bytes.length, written);
      throw Platform.get().assertionError(message, e);
    }
    return b.toByteArray();
  }

  public static <T> byte[] writeList(Buffer.Writer<T> writer, List<T> value) {
    if (value.isEmpty()) return new byte[] {'[', ']'};
    Buffer result = new Buffer(sizeInBytes(writer, value));
    writeList(writer, value, result);
    return result.toByteArray();
  }

  public static <T> void writeList(Buffer.Writer<T> writer, List<T> value, Buffer b) {
    b.writeByte('[');
    for (int i = 0, length = value.size(); i < length; ) {
      writer.write(value.get(i++), b);
      if (i < length) b.writeByte(',');
    }
    b.writeByte(']');
  }

  public static <T> byte[] writeNestedList(Buffer.Writer<T> writer, List<List<T>> traces) {
    // Get the encoded size of the nested list so that we don't need to grow the buffer
    int length = traces.size();
    int sizeInBytes = 2; // []
    if (length > 1) sizeInBytes += length - 1; // comma to join elements

    for (int i = 0; i < length; i++) {
      List<T> spans = traces.get(i);
      int jLength = spans.size();
      sizeInBytes += 2; // []
      if (jLength > 1) sizeInBytes += jLength - 1; // comma to join elements
      for (int j = 0; j < jLength; j++) {
        sizeInBytes += writer.sizeInBytes(spans.get(j));
      }
    }

    Buffer out = new Buffer(sizeInBytes);
    out.writeByte('['); // start list of traces
    for (int i = 0; i < length; i++) {
      writeList(writer, traces.get(i), out);
      if (i + 1 < length) out.writeByte(',');
    }
    out.writeByte(']'); // stop list of traces
    return out.toByteArray();
  }

  static IllegalArgumentException exceptionReading(String type, byte[] bytes, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("malformed") != -1) cause = "Malformed";
    String message = format("%s reading %s from json: %s", cause, type, new String(bytes, UTF_8));
    throw new IllegalArgumentException(message, e);
  }
}
