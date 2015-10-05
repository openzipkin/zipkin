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
import io.zipkin.Dependencies;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import java.io.IOException;
import okio.Buffer;
import okio.ByteString;

import static io.zipkin.internal.Util.UTF_8;

/**
 * This explicitly constructs instances of model classes via manual parsing for a number of
 * reasons.
 *
 * <ul> <li>Eliminates the need to keep separate model classes for thrift vs json</li> <li>Avoids
 * magic field initialization which, can miss constructor guards</li> <li>Allows us to safely re-use
 * the json form in toString methods</li> <li>Encourages logic to be based on the thrift shape of
 * the objects</li> </ul>
 *
 * <p/> There is the up-front cost of creating this, and maintenance of this to consider. However,
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

    @Override
    public String toString() {
      return "JsonAdapter(HexLong)";
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
          (value.ipv4 >> 24 & 0xff),
          (value.ipv4 >> 16 & 0xff),
          (value.ipv4 >> 8 & 0xff),
          (value.ipv4 & 0xff)
      );
      writer.name("ipv4").value(ipv4);
      if (value.port != 0) {
        writer.name("port").value(value.port & 0xffff);
      }
      writer.endObject();
    }

    @Override
    public String toString() {
      return "JsonAdapter(Endpoint)";
    }
  };

  public static final JsonAdapter<Annotation> ANNOTATION_ADAPTER =
      new Moshi.Builder()
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
                result.value(reader.nextBoolean() ? new byte[]{0} : new byte[]{1});
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
          result.build();
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

    @Override
    public String toString() {
      return "JsonAdapter(BinaryAnnotation)";
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
      writer.name("annotations");
      writer.beginArray();
      for (Annotation a : value.annotations) {
        ANNOTATION_ADAPTER.toJson(writer, a);
      }
      writer.endArray();
      writer.name("binaryAnnotations");
      writer.beginArray();
      for (BinaryAnnotation b : value.binaryAnnotations) {
        BINARY_ANNOTATION_ADAPTER.toJson(writer, b);
      }
      writer.endArray();
      if (value.debug != null) {
        writer.name("debug").value(value.debug);
      }
      writer.endObject();
    }

    @Override
    public String toString() {
      return "JsonAdapter(Span)";
    }
  };

  @Override
  public Span readSpan(byte[] bytes) {
    return read(SPAN_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SPAN_ADAPTER, value);
  }

  public static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER =
      new Moshi.Builder().build().adapter(DependencyLink.class);

  public static final JsonAdapter<Dependencies> DEPENDENCIES_ADAPTER =
      new Moshi.Builder().build().adapter(Dependencies.class);

  @Override
  public Dependencies readDependencies(byte[] bytes) {
    return read(DEPENDENCIES_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencies(Dependencies value) {
    return write(DEPENDENCIES_ADAPTER, value);
  }

  private <T> T read(JsonAdapter<T> adapter, byte[] bytes) {
    Buffer buffer = new Buffer();
    buffer.write(bytes);
    try {
      return adapter.fromJson(buffer);
    } catch (IOException e) {
      return null;
    }
  }

  private <T> byte[] write(JsonAdapter<T> adapter, T value) {
    Buffer buffer = new Buffer();
    try {
      adapter.toJson(buffer, value);
    } catch (IOException e) {
      return null;
    }
    return buffer.readByteArray();
  }
}
