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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;

import static java.lang.Double.doubleToRawLongBits;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.assertionError;
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
      return Util.lowerHexToUnsignedLong(reader.nextString());
    }

    @Override
    public void toJson(JsonWriter writer, Long value) throws IOException {
      writer.value(Util.toLowerHex(value));
    }
  };

  static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override
    public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("serviceName")) {
          result.serviceName(reader.nextString());
        } else if (nextName.equals("ipv4")) {
          String[] ipv4String = reader.nextString().split("\\.", 5);
          int ipv4 = 0;
          for (String b : ipv4String) {
            ipv4 = ipv4 << 8 | (Integer.parseInt(b) & 0xff);
          }
          result.ipv4(ipv4);
        } else if (nextName.equals("ipv6")) {
          String input = reader.nextString();
          // Shouldn't hit DNS, because it's an IP string literal.
          byte[] ipv6 = InetAddress.getByName(input).getAddress();
          result.ipv6(ipv6);
        } else if (nextName.equals("port")) {
          result.port((short) reader.nextInt());
        } else {
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
      if (value.ipv6 != null) {
        writer.name("ipv6").value(InetAddress.getByAddress(value.ipv6).getHostAddress());
      }
      writer.endObject();
    }
  }.nullSafe();

  static final JsonAdapter<Long> NULLABLE_LONG_ADAPTER = new JsonAdapter<Long>() {
    @Override public Long fromJson(JsonReader reader) throws IOException {
      return reader.nextLong();
    }

    @Override public void toJson(JsonWriter writer, Long value) throws IOException {
      writer.value(value.longValue());
    }

    @Override public String toString() {
      return "Long";
    }
  }.nullSafe();

  static final JsonAdapter<Boolean> NULLABLE_BOOLEAN_ADAPTER = new JsonAdapter<Boolean>() {
    @Override public Boolean fromJson(JsonReader reader) throws IOException {
      return reader.nextBoolean();
    }

    @Override public void toJson(JsonWriter writer, Boolean value) throws IOException {
      writer.value(value.booleanValue());
    }

    @Override public String toString() {
      return "Boolean";
    }
  }.nullSafe();

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override
    public Annotation fromJson(JsonReader reader) throws IOException {
      Annotation.Builder result = Annotation.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("timestamp")) {
          result.timestamp(reader.nextLong());
        } else if (nextName.equals("value")) {
          result.value(reader.nextString());
        } else if (nextName.equals("endpoint")) {
          result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, Annotation value) throws IOException {
      writer.beginObject();
      writer.name("timestamp").value(value.timestamp);
      writer.name("value").value(value.value);
      if (value.endpoint != null) {
        writer.name("endpoint");
        ENDPOINT_ADAPTER.toJson(writer, value.endpoint);
      }
      writer.endObject();
    }

    @Override
    public String toString() {
      return "Annotation";
    }
  };

  static final JsonAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new JsonAdapter<BinaryAnnotation>() {

    @Override
    public BinaryAnnotation fromJson(JsonReader reader) throws IOException {
      BinaryAnnotation.Builder result = BinaryAnnotation.builder();
      Double number = null;
      String string = null;
      Type type = Type.STRING;
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("key")) {
          result.key(reader.nextString());
        } else if (nextName.equals("value")) {
          switch (reader.peek()) {
            case BOOLEAN:
              type = Type.BOOL;
              result.value(reader.nextBoolean() ? new byte[] {1} : new byte[] {0});
              break;
            case STRING:
              string = reader.nextString();
              break;
            case NUMBER:
              number = reader.nextDouble();
              break;
            default:
              throw new MalformedJsonException(
                  "Expected value to be a boolean, string or number but was " + reader.peek()
                      + " at path " + reader.getPath());
          }
        } else if (nextName.equals("type")) {
          type = Type.valueOf(reader.nextString());
        } else if (nextName.equals("endpoint")) {
          result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
        } else {
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
          return result.value(Base64.decode(string)).build();
        default:
          break;
      }
      final byte[] value;
      if (type == Type.I16) {
        short v = number.shortValue();
        value = ByteBuffer.allocate(2).putShort(0, v).array();
      } else if (type == Type.I32) {
        int v = number.intValue();
        value = ByteBuffer.allocate(4).putInt(0, v).array();
      } else if (type == Type.I64 || type == Type.DOUBLE) {
        long v = type == Type.I64 ? number.longValue() : doubleToRawLongBits(number);
        value = ByteBuffer.allocate(8).putLong(0, v).array();
      } else {
        throw new AssertionError("BinaryAnnotationType " + type + " was added, but not handled");
      }
      return result.value(value).build();
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
          writer.value(Base64.encodeUrl(value.value));
          break;
        case I16:
          writer.value(ByteBuffer.wrap(value.value).getShort());
          break;
        case I32:
          writer.value(ByteBuffer.wrap(value.value).getInt());
          break;
        case I64:
          writer.value(ByteBuffer.wrap(value.value).getLong());
          break;
        case DOUBLE:
          writer.value(Double.longBitsToDouble(ByteBuffer.wrap(value.value).getLong()));
          break;
        default:
      }
      if (value.type != Type.STRING && value.type != Type.BOOL) {
        writer.name("type").value(value.type.name());
      }
      if (value.endpoint != null) {
        writer.name("endpoint");
        ENDPOINT_ADAPTER.toJson(writer, value.endpoint);
      }
      writer.endObject();
    }
  };

  static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override
    public Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("traceId")) {
          result.traceId(HEX_LONG_ADAPTER.fromJson(reader));
        } else if (nextName.equals("name")) {
          result.name(reader.nextString());
        } else if (nextName.equals("id")) {
          result.id(HEX_LONG_ADAPTER.fromJson(reader));
        } else if (nextName.equals("parentId")) {
          if (reader.peek() != JsonToken.NULL) {
            result.parentId(HEX_LONG_ADAPTER.fromJson(reader));
          } else {
            reader.skipValue();
          }
        } else if (nextName.equals("timestamp")) {
          result.timestamp(NULLABLE_LONG_ADAPTER.fromJson(reader));
        } else if (nextName.equals("duration")) {
          result.duration(NULLABLE_LONG_ADAPTER.fromJson(reader));
        } else if (nextName.equals("annotations")) {
          reader.beginArray();
          while (reader.hasNext()) {
            result.addAnnotation(ANNOTATION_ADAPTER.fromJson(reader));
          }
          reader.endArray();
        } else if (nextName.equals("binaryAnnotations")) {
          reader.beginArray();
          while (reader.hasNext()) {
            result.addBinaryAnnotation(BINARY_ANNOTATION_ADAPTER.fromJson(reader));
          }
          reader.endArray();
        } else if (nextName.equals("debug")) {
          result.debug(NULLABLE_BOOLEAN_ADAPTER.fromJson(reader));
        } else {
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
      return SPAN_ADAPTER.fromJson(jsonReader(bytes));
    } catch (Exception e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  @Override
  public byte[] writeSpan(Span value) {
    Buffer out = new Buffer();
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
    write(SPAN_ADAPTER, value, writer);
    closeQuietly(writer);
    return out.toByteArray();
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
    Buffer out = new Buffer();
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
    writer.setLenient(true); // multiple top-level values
    out.write('['); // start list of traces
    for (Iterator<List<Span>> trace = traces.iterator(); trace.hasNext(); ) {

      out.write('['); // start trace
      // write each span
      for (Iterator<Span> span = trace.next().iterator(); span.hasNext(); ) {
        write(SPAN_ADAPTER, span.next(), writer);
        flushQuietly(writer);

        if (span.hasNext()) out.write(',');
      }
      out.write(']'); // stop trace

      if (trace.hasNext()) out.write(',');
    }
    out.write(']'); // stop list of traces
    return out.toByteArray();
  }

  public List<List<Span>> readTraces(byte[] bytes) {
    JsonReader reader = jsonReader(bytes);
    List<List<Span>> result = new LinkedList<List<Span>>(); // cause we don't know how long it will be
    try {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginArray();
        List<Span> trace = new LinkedList<Span>(); // cause we don't know how long it will be
        while (reader.hasNext()) {
          trace.add(SPAN_ADAPTER.fromJson(reader));
        }
        reader.endArray();
        result.add(trace);
      }
      reader.endArray();
      return result;
    } catch (Exception e) {
      throw exceptionReading("List<List<Span>>", bytes, e);
    }
  }

  static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {

    @Override
    public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("parent")) {
          result.parent(reader.nextString());
        } else if (nextName.equals("child")) {
          result.child(reader.nextString());
        } else if (nextName.equals("callCount")) {
          result.callCount(reader.nextLong());
        } else {
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
  @Override
  public DependencyLink readDependencyLink(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading DependencyLink");
    try {
      return DEPENDENCY_LINK_ADAPTER.fromJson(jsonReader(bytes));
    } catch (Exception e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  // Added since JSON-based storage usually works better with single documents rather than
  // a large encoded list.
  @Override
  public byte[] writeDependencyLink(DependencyLink value) {
    Buffer out = new Buffer();
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
    write(DEPENDENCY_LINK_ADAPTER, value, writer);
    closeQuietly(writer);
    return out.toByteArray();
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

  @Override public byte[] gather(List<byte[]> values) {
    int sizeOfArray = 2;
    int length = values.size();
    for (int i = 0; i < length; ) {
      sizeOfArray += values.get(i++).length;
      if (i < length) sizeOfArray++;
    }

    Buffer out = new Buffer(sizeOfArray);
    out.write('[');
    for (int i = 0; i < length; ) {
      out.write(values.get(i++));
      if (i < length) out.write(',');
    }
    out.write(']');
    return out.toByteArray();
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
    JsonReader reader = jsonReader(bytes);
    List<T> result;
    try {
      reader.beginArray();
      if (reader.hasNext()) {
        result = new LinkedList<T>(); // cause we don't know how long it will be
      } else {
        result = Collections.emptyList();
      }
      while (reader.hasNext()) {
        result.add(adapter.fromJson(reader));
      }
      reader.endArray();
      return result;
    } catch (Exception e) {
      throw exceptionReading("List<" + adapter + ">", bytes, e);
    }
  }

  private static JsonReader jsonReader(byte[] bytes) {
    return new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
  }

  static <T> byte[] writeList(JsonAdapter<T> adapter, List<T> values) {
    Buffer out = new Buffer();
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
    writer.setLenient(true); // multiple top-level values

    out.write('[');
    int length = values.size();
    for (int i = 0; i < length; ) {
      write(adapter, values.get(i++), writer);
      flushQuietly(writer);
      if (i < length) out.write(',');
    }
    out.write(']');
    return out.toByteArray();
  }

  private static void flushQuietly(JsonWriter writer) {
    try {
      writer.flush();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static void closeQuietly(JsonWriter writer) {
    try {
      writer.close();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /** Inability to encode is a programming bug. */
  static <T> void write(JsonAdapter<T> adapter, T value, JsonWriter writer) {
    try {
      adapter.toJson(writer, value);
    } catch (Exception e) {
      throw assertionError("Could not write " + value + " as json", e);
    }
  }

  static IllegalArgumentException exceptionReading(String type, byte[] bytes, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("malformed") != -1) cause = "Malformed";
    String message = String.format("%s reading %s from json: %s", cause, type, new String(bytes, UTF_8));
    throw new IllegalArgumentException(message, e);
  }

  static abstract class JsonAdapter<T> {
    abstract T fromJson(JsonReader reader) throws IOException;

    public abstract void toJson(JsonWriter writer, T value) throws IOException;

    /**
     * Returns a JSON adapter equal to this JSON adapter, but with support for reading and writing
     * nulls. Borrowed pattern from moshi
     */
    public final JsonAdapter<T> nullSafe() {
      final JsonAdapter<T> delegate = this;
      return new JsonAdapter<T>() {
        @Override public T fromJson(JsonReader reader) throws IOException {
          if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
          } else {
            return delegate.fromJson(reader);
          }
        }
        @Override public void toJson(JsonWriter writer, T value) throws IOException {
          if (value == null) {
            writer.nullValue();
          } else {
            delegate.toJson(writer, value);
          }
        }
        @Override public String toString() {
          return delegate + ".nullSafe()";
        }
      };
    }
  }
}
