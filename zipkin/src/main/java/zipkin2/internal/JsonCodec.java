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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.gson.stream.JsonToken.BOOLEAN;
import static com.google.gson.stream.JsonToken.NULL;
import static com.google.gson.stream.JsonToken.STRING;
import static java.lang.String.format;

/**
 * This explicitly constructs instances of model classes via manual parsing for a number of
 * reasons.
 *
 * <ul>
 *   <li>Eliminates the need to keep separate model classes for proto3 vs json
 *   <li>Avoids magic field initialization which, can miss constructor guards
 *   <li>Allows us to safely re-use the json form in toString methods
 *   <li>Encourages logic to be based on the json shape of objects
 *   <li>Ensures the order and naming of the fields in json is stable
 * </ul>
 *
 * <p>There is the up-front cost of creating this, and maintenance of this to consider. However,
 * this should be easy to justify as these objects don't change much at all.
 */
public final class JsonCodec {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  // Hides gson types for internal use in other submodules
  public static final class JsonReader {
    final com.google.gson.stream.JsonReader delegate;

    JsonReader(ReadBuffer buffer) {
      delegate = new com.google.gson.stream.JsonReader(new InputStreamReader(buffer, UTF_8));
    }

    public void beginArray() throws IOException {
      delegate.beginArray();
    }

    public boolean hasNext() throws IOException {
      return delegate.hasNext();
    }

    public void endArray() throws IOException {
      delegate.endArray();
    }

    public void beginObject() throws IOException {
      delegate.beginObject();
    }

    public void endObject() throws IOException {
      delegate.endObject();
    }

    public String nextName() throws IOException {
      return delegate.nextName();
    }

    public String nextString() throws IOException {
      return delegate.nextString();
    }

    public void skipValue() throws IOException {
      delegate.skipValue();
    }

    public long nextLong() throws IOException {
      return delegate.nextLong();
    }

    public String getPath() {
      return delegate.getPath();
    }

    public boolean nextBoolean() throws IOException {
      return delegate.nextBoolean();
    }

    public int nextInt() throws IOException {
      return delegate.nextInt();
    }

    public boolean peekString() throws IOException {
      return delegate.peek() == STRING;
    }

    public boolean peekBoolean() throws IOException {
      return delegate.peek() == BOOLEAN;
    }

    public boolean peekNull() throws IOException {
      return delegate.peek() == NULL;
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  public interface JsonReaderAdapter<T> {
    T fromJson(JsonReader reader) throws IOException;
  }

  public static <T> boolean read(
    JsonReaderAdapter<T> adapter, ReadBuffer buffer, Collection<T> out) {
    if (buffer.available() == 0) return false;
    try {
      out.add(adapter.fromJson(new JsonReader(buffer)));
      return true;
    } catch (Exception e) {
      throw exceptionReading(adapter.toString(), e);
    }
  }

  public static @Nullable <T> T readOne(JsonReaderAdapter<T> adapter, ReadBuffer buffer) {
    List<T> out = new ArrayList<>(1); // TODO: could make single-element list w/o array
    if (!read(adapter, buffer, out)) return null;
    return out.get(0);
  }

  public static <T> boolean readList(
    JsonReaderAdapter<T> adapter, ReadBuffer buffer, Collection<T> out) {
    if (buffer.available() == 0) return false;
    JsonReader reader = new JsonReader(buffer);
    try {
      reader.beginArray();
      if (!reader.hasNext()) return false;
      while (reader.hasNext()) out.add(adapter.fromJson(reader));
      reader.endArray();
      return true;
    } catch (Exception e) {
      throw exceptionReading("List<" + adapter + ">", e);
    }
  }

  static <T> int sizeInBytes(WriteBuffer.Writer<T> writer, List<T> value) {
    int length = value.size();
    int sizeInBytes = 2; // []
    if (length > 1) sizeInBytes += length - 1; // comma to join elements
    for (int i = 0; i < length; i++) {
      sizeInBytes += writer.sizeInBytes(value.get(i));
    }
    return sizeInBytes;
  }

  /** Inability to encode is a programming bug. */
  public static <T> byte[] write(WriteBuffer.Writer<T> writer, T value) {
    byte[] result = new byte[writer.sizeInBytes(value)];
    WriteBuffer b = WriteBuffer.wrap(result);
    try {
      writer.write(value, b);
    } catch (RuntimeException e) {
      int lengthWritten = result.length;
      for (int i = 0; i < result.length; i++) {
        if (result[i] == 0) {
          lengthWritten = i;
          break;
        }
      }

      // Don't use value directly in the message, as its toString might be implemented using this
      // method. If that's the case, we'd stack overflow. Instead, emit what we've written so far.
      String message =
        format(
          "Bug found using %s to write %s as json. Wrote %s/%s bytes: %s",
          writer.getClass().getSimpleName(),
          value.getClass().getSimpleName(),
          lengthWritten,
          result.length,
          new String(result, 0, lengthWritten, UTF_8));
      throw Platform.get().assertionError(message, e);
    }
    return result;
  }

  public static <T> byte[] writeList(WriteBuffer.Writer<T> writer, List<T> value) {
    if (value.isEmpty()) return new byte[] {'[', ']'};
    byte[] result = new byte[sizeInBytes(writer, value)];
    writeList(writer, value, WriteBuffer.wrap(result));
    return result;
  }

  public static <T> int writeList(WriteBuffer.Writer<T> writer, List<T> value, byte[] out,
    int pos) {
    if (value.isEmpty()) {
      out[pos++] = '[';
      out[pos++] = ']';
      return 2;
    }
    int initialPos = pos;
    WriteBuffer result = WriteBuffer.wrap(out, pos);
    writeList(writer, value, result);
    return result.pos() - initialPos;
  }

  public static <T> void writeList(WriteBuffer.Writer<T> writer, List<T> value, WriteBuffer b) {
    b.writeByte('[');
    for (int i = 0, length = value.size(); i < length; ) {
      writer.write(value.get(i++), b);
      if (i < length) b.writeByte(',');
    }
    b.writeByte(']');
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("Expected BEGIN_OBJECT") != -1 || cause.indexOf("malformed") != -1) {
      cause = "Malformed";
    }
    String message = format("%s reading %s from json", cause, type);
    throw new IllegalArgumentException(message, e);
  }
}
