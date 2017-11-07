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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

/**
 * This explicitly constructs instances of model classes via manual parsing for a number of
 * reasons.
 *
 * <ul>
 *   <li>Eliminates the need to keep separate model classes for thrift vs json</li>
 *   <li>Avoids magic field initialization which, can miss constructor guards</li>
 *   <li>Allows us to safely re-use the json form in toString methods</li>
 *   <li>Encourages logic to be based on the thrift shape of objects</li>
 *   <li>Ensures the order and naming of the fields in json is stable</li>
 * </ul>
 *
 * <p> There is the up-front cost of creating this, and maintenance of this to consider. However,
 * this should be easy to justify as these objects don't change much at all.
 */
public final class JsonCodec {
  // Hides gson types for internal use in other submodules
  public static final class JsonReader {
    final com.google.gson.stream.JsonReader delegate;

    JsonReader(byte[] bytes) {
      delegate = new com.google.gson.stream.JsonReader(
        new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8));
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

    public String getPath() throws IOException {
      return delegate.getPath();
    }

    public boolean nextBoolean() throws IOException {
      return delegate.nextBoolean();
    }

    public int nextInt() throws IOException {
      return delegate.nextInt();
    }

    public boolean peekNull() throws IOException {
      return delegate.peek() == com.google.gson.stream.JsonToken.NULL;
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final Charset UTF_8 = Charset.forName("UTF-8");

  public interface JsonReaderAdapter<T> {
    T fromJson(JsonReader reader) throws IOException;
  }

  public static <T> boolean read(JsonReaderAdapter<T> adapter, byte[] bytes, Collection<T> out) {
    if (bytes.length == 0) return false;
    try {
      out.add(adapter.fromJson(new JsonReader(bytes)));
      return true;
    } catch (Exception e) {
      throw exceptionReading(adapter.toString(), e);
    }
  }

  public static @Nullable <T> T readOne(JsonReaderAdapter<T> adapter, byte[] bytes) {
    List<T> out = new ArrayList<>(1); // TODO: could make single-element list w/o array
    if (!read(adapter, bytes, out)) return null;
    return out.get(0);
  }

  public static <T> boolean readList(JsonReaderAdapter<T> adapter, byte[] bytes,
    Collection<T> out) {
    if (bytes.length == 0) return false;
    JsonReader reader = new JsonReader(bytes);
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

  public static <T> List<T> readList(JsonReaderAdapter<T> adapter, byte[] bytes) {
    List<T> out = new ArrayList<>();
    if (!readList(adapter, bytes, out)) return Collections.emptyList();
    return out;
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

  public static <T> int writeList(Buffer.Writer<T> writer, List<T> value, byte[] out, int pos) {
    if (value.isEmpty()) {
      out[pos++] = '[';
      out[pos++] = ']';
      return 2;
    }
    int length = sizeInBytes(writer, value);
    Buffer result = new Buffer(out, pos);
    writeList(writer, value, result);
    return length;
  }

  public static <T> void writeList(Buffer.Writer<T> writer, List<T> value, Buffer b) {
    b.writeByte('[');
    for (int i = 0, length = value.size(); i < length; ) {
      writer.write(value.get(i++), b);
      if (i < length) b.writeByte(',');
    }
    b.writeByte(']');
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("malformed") != -1) cause = "Malformed";
    String message = format("%s reading %s from json", cause, type);
    throw new IllegalArgumentException(message, e);
  }
}
