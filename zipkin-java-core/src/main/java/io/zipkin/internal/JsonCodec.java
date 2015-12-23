/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.internal;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.Codec;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import okio.Buffer;
import okio.ByteString;

import static io.zipkin.internal.Util.UTF_8;
import static java.util.logging.Level.FINEST;

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
 * <p/> There is the up-front cost of creating this, and maintenance of this to consider. However,
 * this should be easy to justify as these objects don't change much at all.
 */
public final class JsonCodec implements Codec {
  private static final Logger LOGGER = Logger.getLogger(JsonCodec.class.getName());

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
      Endpoint.Builder result = new Endpoint.Builder();
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
      BinaryAnnotation.Builder result = new BinaryAnnotation.Builder();
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
                result.value(reader.nextBoolean() ? new byte[]{1} : new byte[]{0});
                break;
              case STRING:
                string = reader.nextString();
                break;
              case NUMBER:
                number = reader.nextDouble();
                break;
              default:
                return null;
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
          return null;
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
      Span.Builder result = new Span.Builder();
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
  };

  @Override
  public Span readSpan(byte[] bytes) {
    try {
      return SPAN_ADAPTER.fromJson(new Buffer().write(bytes));
    } catch (Exception e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Could not read Span from json" + new String(bytes, UTF_8), e);
      }
      return null;
    }
  }

  @Override
  public byte[] writeSpan(Span value) {
    Buffer buffer = new Buffer();
    return write(SPAN_ADAPTER, value, buffer) ? buffer.readByteArray() : null;
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
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
        if (!write(SPAN_ADAPTER, span.next(), buffer)) return null;
        if (span.hasNext()) buffer.writeUtf8CodePoint(',');
      }
      buffer.writeUtf8CodePoint(']'); // stop trace

      if (trace.hasNext()) buffer.writeUtf8CodePoint(',');
    }
    buffer.writeUtf8CodePoint(']'); // stop list of traces
    return buffer.readByteArray();
  }

  public static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {

    @Override
    public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = new DependencyLink.Builder();
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
  };

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    return readList(DEPENDENCY_LINK_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    return writeList(DEPENDENCY_LINK_ADAPTER, value);
  }

  private static <T> List<T> readList(JsonAdapter<T> adapter, byte[] bytes) {
    JsonReader reader = JsonReader.of(new Buffer().write(bytes));
    List<T> result = new LinkedList<>(); // cause we don't know how long it will be
    try {
      reader.beginArray();
      while (reader.hasNext()) {
        T next = adapter.fromJson(reader);
        if (next == null) return null;
        result.add(next);
      }
      reader.endArray();
      return result;
    } catch (Exception e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Could not read " + adapter + " from json" + new String(bytes, UTF_8), e);
      }
      return null;
    }
  }

  /** Returns null if any element could not be written. */
  @Nullable
  private static <T> byte[] writeList(JsonAdapter<T> adapter, List<T> values) {
    Buffer buffer = new Buffer();
    buffer.writeUtf8CodePoint('[');
    for (Iterator<T> i = values.iterator(); i.hasNext(); ) {
      if (!write(adapter, i.next(), buffer)) return null;
      if (i.hasNext()) buffer.writeUtf8CodePoint(',');
    }
    buffer.writeUtf8CodePoint(']');
    return buffer.readByteArray();
  }

  /** Returns false when the value could not be written */
  private static <T> boolean write(JsonAdapter<T> adapter, T value, Buffer buffer) {
    try {
      adapter.toJson(JsonWriter.of(buffer), value);
      return true;
    } catch (Exception e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Could not write " + value + " as json", e);
      }
      return false;
    }
  }
}
