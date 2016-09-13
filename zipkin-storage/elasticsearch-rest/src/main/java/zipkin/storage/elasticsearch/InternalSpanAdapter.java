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
package zipkin.storage.elasticsearch;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.ByteString;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.JsonCodec;
import zipkin.internal.Util;

import static zipkin.internal.Util.UTF_8;

/**
 * Acts the same as {@link JsonCodec}, except it writes timestamp_millis field.
 *
 * <p>Code resurrected from before we switched to Java 6 as storage components can be Java 7+
 */
final class InternalSpanAdapter extends JsonAdapter<Span> {
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
          if (reader.peek() != JsonReader.Token.NULL) {
            result.parentId(HEX_LONG_ADAPTER.fromJson(reader));
          } else {
            reader.skipValue();
          }
          break;
        case "timestamp":
          result.timestamp(NULLABLE_LONG_ADAPTER.fromJson(reader));
          break;
        case "duration":
          result.duration(NULLABLE_LONG_ADAPTER.fromJson(reader));
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
          result.debug(NULLABLE_BOOLEAN_ADAPTER.fromJson(reader));
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
      writer.name("timestamp_millis").value(value.timestamp / 1000);
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
          case "ipv6":
            String input = reader.nextString();
            // Shouldn't hit DNS, because it's an IP string literal.
            byte[] ipv6 = InetAddress.getByName(input).getAddress();
            result.ipv6(ipv6);
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
  }.nullSafe();

  static final JsonAdapter<Boolean> NULLABLE_BOOLEAN_ADAPTER = new JsonAdapter<Boolean>() {
    @Override public Boolean fromJson(JsonReader reader) throws IOException {
      return reader.nextBoolean();
    }

    @Override public void toJson(JsonWriter writer, Boolean value) throws IOException {
      writer.value(value.booleanValue());
    }
  }.nullSafe();

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override
    public Annotation fromJson(JsonReader reader) throws IOException {
      Annotation.Builder result = Annotation.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "timestamp":
            result.timestamp(reader.nextLong());
            break;
          case "value":
            result.value(reader.nextString());
            break;
          case "endpoint":
            result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
            break;
          default:
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
  };

  static final JsonAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new JsonAdapter<BinaryAnnotation>() {
    @Override
    public BinaryAnnotation fromJson(JsonReader reader) throws IOException {
      BinaryAnnotation.Builder result = BinaryAnnotation.builder();
      String number = null;
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
                number = reader.nextString();
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
        default:
          break;
      }
      Buffer buffer = new Buffer();
      switch (type) {
        case I16:
          buffer.writeShort(Short.parseShort(number));
          break;
        case I32:
          buffer.writeInt(Integer.parseInt(number));
          break;
        case I64:
        case DOUBLE:
          long v = type == BinaryAnnotation.Type.I64
                          ? Long.parseLong(number)
                          : Double.doubleToRawLongBits(Double.parseDouble(number));
          buffer.writeLong(v);
          break;
        default:
          throw new AssertionError(
              "BinaryAnnotationType " + type + " was added, but not handled");
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
      if (value.type != BinaryAnnotation.Type.STRING
          && value.type != BinaryAnnotation.Type.BOOL) {
        writer.name("type").value(value.type.name());
      }
      if (value.endpoint != null) {
        writer.name("endpoint");
        ENDPOINT_ADAPTER.toJson(writer, value.endpoint);
      }
      writer.endObject();
    }
  };
}