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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import okio.Buffer;
import okio.ByteString;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkArgument;

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
public final class JsonCodec implements Codec {

  static final JsonAdapter<Long> HEX_LONG_ADAPTER = new JsonAdapter<Long>() {
    @Override
    public Long fromJson(JsonReader reader) throws IOException {
      Buffer buffer = new Buffer();
      buffer.writeUtf8(reader.nextString());
      return buffer.readHexadecimalUnsignedLong();
    }

    @Override
    public void toJson(JsonWriter writer, Long value) throws IOException {
      writer.value(String.format("%016x", value));
    }
  };

  public static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override
    public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "serviceName":
            result.serviceName(reader.nextString());
            break;
          case "ipv4":
            String[] ipv4String = reader.nextString().split("\\.", 5);
            int ipv4 = 0;
            for (String b : ipv4String) {
              ipv4 = ipv4 << 8 | (Integer.parseInt(b) & 0xff);
            }
            result.ipv4(ipv4);
            break;
          case "port":
            result.port((short) reader.nextInt());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, Endpoint value) throws IOException {
      writer.beginObject();
      writer.name("serviceName").value(value.serviceName);
      String ipv4 = String.format("%d.%d.%d.%d",
          value.ipv4 >> 24 & 0xff,
          value.ipv4 >> 16 & 0xff,
          value.ipv4 >> 8 & 0xff,
          value.ipv4 & 0xff
      );
      writer.name("ipv4").value(ipv4);
      if (value.port != null) {
        int port = value.port & 0xffff;
        if (port != 0) writer.name("port").value(port);
      }
      writer.endObject();
    }
  };

  public static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new Moshi.Builder()
      .add(Endpoint.class, ENDPOINT_ADAPTER.nullSafe())
      .build().adapter(Annotation.class);

  public static final JsonAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new JsonAdapter<BinaryAnnotation>() {

    @Override
    public BinaryAnnotation fromJson(JsonReader reader) throws IOException {
      BinaryAnnotation.Builder result = BinaryAnnotation.builder();
      Double number = null;
      String string = null;
      BinaryAnnotation.Type type = BinaryAnnotation.Type.STRING;
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "key":
            result.key(reader.nextString());
            break;
          case "value":
            switch (reader.peek()) {
              case BOOLEAN:
                type = BinaryAnnotation.Type.BOOL;
                result.value(reader.nextBoolean() ? new byte[] {1} : new byte[] {0});
                break;
              case STRING:
                string = reader.nextString();
                break;
              case NUMBER:
                number = reader.nextDouble();
                break;
              default:
                throw new JsonDataException(
                    "Expected value to be a boolean, string or number but was " + reader.peek()
                        + " at path " + reader.getPath());
            }
            break;
          case "type":
            type = BinaryAnnotation.Type.valueOf(reader.nextString());
            break;
          case "endpoint":
            result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      result.type(type);
      switch (type) {
        case BOOL:
          return result.build();
        case STRING:
          return result.value(string.getBytes(UTF_8)).build();
        case BYTES:
          return result.value(ByteString.decodeBase64(string).toByteArray()).build();
      }
      Buffer buffer = new Buffer();
      switch (type) {
        case I16:
          buffer.writeShort(number.shortValue());
          break;
        case I32:
          buffer.writeInt(number.intValue());
          break;
        case I64:
          buffer.writeLong(number.longValue());
          break;
        case DOUBLE:
          buffer.writeLong(Double.doubleToRawLongBits(number));
          break;
        default:
          throw new AssertionError("BinaryAnnotationType " + type + " was added, but not handled");
      }
      return result.value(buffer.readByteArray()).build();
    }

    @Override
    public void toJson(JsonWriter writer, BinaryAnnotation value) throws IOException {
      writer.beginObject();
      writer.name("key").value(value.key);
      writer.name("value");
      switch (value.type) {
        case BOOL:
          writer.value(value.value[0] == 1);
          break;
        case STRING:
          writer.value(new String(value.value, UTF_8));
          break;
        case BYTES:
          writer.value(new Buffer().write(value.value).readByteString().base64Url());
          break;
        case I16:
          writer.value(new Buffer().write(value.value).readShort());
          break;
        case I32:
          writer.value(new Buffer().write(value.value).readInt());
          break;
        case I64:
          writer.value(new Buffer().write(value.value).readLong());
          break;
        case DOUBLE:
          writer.value(Double.longBitsToDouble(new Buffer().write(value.value).readLong()));
          break;
        default:
      }
      if (value.type != BinaryAnnotation.Type.STRING && value.type != BinaryAnnotation.Type.BOOL) {
        writer.name("type").value(value.type.name());
      }
      if (value.endpoint != null) {
        writer.name("endpoint");
        ENDPOINT_ADAPTER.toJson(writer, value.endpoint);
      }
      writer.endObject();
    }
  };

  public static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override
    public Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "traceId":
            result.traceId(HEX_LONG_ADAPTER.fromJson(reader));
            break;
          case "name":
            result.name(reader.nextString());
            break;
          case "id":
            result.id(HEX_LONG_ADAPTER.fromJson(reader));
            break;
          case "parentId":
            result.parentId(HEX_LONG_ADAPTER.fromJson(reader));
            break;
          case "timestamp":
            result.timestamp(reader.nextLong());
            break;
          case "duration":
            result.duration(reader.nextLong());
            break;
          case "annotations":
            reader.beginArray();
            while (reader.hasNext()) {
              result.addAnnotation(ANNOTATION_ADAPTER.fromJson(reader));
            }
            reader.endArray();
            break;
          case "binaryAnnotations":
            reader.beginArray();
            while (reader.hasNext()) {
              result.addBinaryAnnotation(BINARY_ANNOTATION_ADAPTER.fromJson(reader));
            }
            reader.endArray();
            break;
          case "debug":
            result.debug(reader.nextBoolean());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, Span value) throws IOException {
      writer.beginObject();
      writer.name("traceId");
      HEX_LONG_ADAPTER.toJson(writer, value.traceId);
      writer.name("name").value(value.name);
      writer.name("id");
      HEX_LONG_ADAPTER.toJson(writer, value.id);
      if (value.parentId != null) {
        writer.name("parentId");
        HEX_LONG_ADAPTER.toJson(writer, value.parentId);
      }
      if (value.timestamp != null) {
        writer.name("timestamp").value(value.timestamp);
      }
      if (value.duration != null) {
        writer.name("duration").value(value.duration);
      }
      writer.name("annotations");
      writer.beginArray();
      for (int i = 0, length = value.annotations.size(); i < length; i++) {
        ANNOTATION_ADAPTER.toJson(writer, value.annotations.get(i));
      }
      writer.endArray();
      writer.name("binaryAnnotations");
      writer.beginArray();
      for (int i = 0, length = value.binaryAnnotations.size(); i < length; i++) {
        BINARY_ANNOTATION_ADAPTER.toJson(writer, value.binaryAnnotations.get(i));
      }
      writer.endArray();
      if (value.debug != null) {
        writer.name("debug").value(value.debug);
      }
      writer.endObject();
    }

    @Override
    public String toString() {
      return "Span";
    }
  };

  @Override
  public Span readSpan(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading Span");
    try {
      return SPAN_ADAPTER.fromJson(new Buffer().write(bytes));
    } catch (IOException | RuntimeException e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  @Override
  public byte[] writeSpan(Span value) {
    Buffer buffer = new Buffer();
    write(SPAN_ADAPTER, value, buffer);
    return buffer.readByteArray();
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<Span>");
    return readList(SPAN_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpans(List<Span> value) {
    return writeList(SPAN_ADAPTER, value);
  }

  @Override
  public byte[] writeTraces(List<List<Span>> traces) {
    Buffer buffer = new Buffer();
    buffer.writeUtf8CodePoint('['); // start list of traces
    for (Iterator<List<Span>> trace = traces.iterator(); trace.hasNext(); ) {

      buffer.writeUtf8CodePoint('['); // start trace
      // write each span
      for (Iterator<Span> span = trace.next().iterator(); span.hasNext(); ) {
        write(SPAN_ADAPTER, span.next(), buffer);
        if (span.hasNext()) buffer.writeUtf8CodePoint(',');
      }
      buffer.writeUtf8CodePoint(']'); // stop trace

      if (trace.hasNext()) buffer.writeUtf8CodePoint(',');
    }
    buffer.writeUtf8CodePoint(']'); // stop list of traces
    return buffer.readByteArray();
  }

  public List<List<Span>> readTraces(byte[] bytes) {
    JsonReader reader = JsonReader.of(new Buffer().write(bytes));
    List<List<Span>> result = new LinkedList<>(); // cause we don't know how long it will be
    try {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginArray();
        List<Span> trace = new LinkedList<>(); // cause we don't know how long it will be
        while (reader.hasNext()) {
          trace.add(SPAN_ADAPTER.fromJson(reader));
        }
        reader.endArray();
        result.add(trace);
      }
      reader.endArray();
      return result;
    } catch (IOException | RuntimeException e) {
      throw exceptionReading("List<List<Span>>", bytes, e);
    }
  }

  public static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {

    @Override
    public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "parent":
            result.parent(reader.nextString());
            break;
          case "child":
            result.child(reader.nextString());
            break;
          case "callCount":
            result.callCount(reader.nextLong());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, DependencyLink value) throws IOException {
      writer.beginObject();
      writer.name("parent").value(value.parent);
      writer.name("child").value(value.child);
      writer.name("callCount").value(value.callCount);
      writer.endObject();
    }

    @Override
    public String toString() {
      return "DependencyLink";
    }
  };

  // Added since JSON-based storage usually works better with single documents rather than
  // a large encoded list.

  /** throws {@linkplain IllegalArgumentException} if the dependency link couldn't be decoded */
  public DependencyLink readDependencyLink(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading DependencyLink");
    try {
      return DEPENDENCY_LINK_ADAPTER.fromJson(new Buffer().write(bytes));
    } catch (IOException | RuntimeException e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  // Added since JSON-based storage usually works better with single documents rather than
  // a large encoded list.
  public byte[] writeDependencyLink(DependencyLink value) {
    Buffer buffer = new Buffer();
    write(DEPENDENCY_LINK_ADAPTER, value, buffer);
    return buffer.readByteArray();
  }

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<DependencyLink>");
    return readList(DEPENDENCY_LINK_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    return writeList(DEPENDENCY_LINK_ADAPTER, value);
  }

  static final JsonAdapter<String> STRING_ADAPTER = new JsonAdapter<String>() {
    @Override
    public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString();
    }

    @Override
    public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }

    @Override
    public String toString() {
      return "String";
    }
  };

  public List<String> readStrings(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<String>");
    return readList(STRING_ADAPTER, bytes);
  }

  public byte[] writeStrings(List<String> value) {
    return writeList(STRING_ADAPTER, value);
  }

  static <T> List<T> readList(JsonAdapter<T> adapter, byte[] bytes) {
    JsonReader reader = JsonReader.of(new Buffer().write(bytes));
    List<T> result;
    try {
      reader.beginArray();
      if (reader.hasNext()) {
        result = new LinkedList<>(); // cause we don't know how long it will be
      } else {
        result = Collections.emptyList();
      }
      while (reader.hasNext()) {
        result.add(adapter.fromJson(reader));
      }
      reader.endArray();
      return result;
    } catch (IOException | RuntimeException e) {
      throw exceptionReading("List<" + adapter + ">", bytes, e);
    }
  }

  static <T> byte[] writeList(JsonAdapter<T> adapter, List<T> values) {
    Buffer buffer = new Buffer();
    buffer.writeUtf8CodePoint('[');
    int length = values.size();
    for (int i = 0; i < length; ) {
      write(adapter, values.get(i++), buffer);
      if (i < length) buffer.writeUtf8CodePoint(',');
    }
    buffer.writeUtf8CodePoint(']');
    return buffer.readByteArray();
  }

  /** Inability to encode is a programming bug. */
  static <T> void write(JsonAdapter<T> adapter, T value, Buffer buffer) {
    try {
      adapter.toJson(JsonWriter.of(buffer), value);
    } catch (IOException | RuntimeException e) {
      throw new AssertionError("Could not write " + value + " as json", e);
    }
  }

  static IllegalArgumentException exceptionReading(String type, byte[] bytes, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("malformed") != -1) cause = "Malformed";
    String message = String.format("%s reading %s from json: %s", cause, type, new String(bytes, UTF_8));
    throw new IllegalArgumentException(message, e);
  }
}
