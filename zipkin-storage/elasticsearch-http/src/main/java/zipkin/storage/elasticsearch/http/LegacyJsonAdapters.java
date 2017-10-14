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
package zipkin.storage.elasticsearch.http;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import okio.Buffer;
import okio.ByteString;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.lowerHexToUnsignedLong;

final class LegacyJsonAdapters {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override @Nullable
    public Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.skipValue();
          continue;
        }
        switch (nextName) {
          case "traceId":
            String traceId = reader.nextString();
            if (traceId.length() == 32) {
              result.traceIdHigh(lowerHexToUnsignedLong(traceId, 0));
            }
            result.traceId(lowerHexToUnsignedLong(traceId));
            break;
          case "name":
            result.name(reader.nextString());
            break;
          case "id":
            result.id(lowerHexToUnsignedLong(reader.nextString()));
            break;
          case "parentId":
            result.parentId(lowerHexToUnsignedLong(reader.nextString()));
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
    public void toJson(JsonWriter writer, @Nullable Span value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER =
    new JsonAdapter<BinaryAnnotation>() {
      @Override @Nullable
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
            if (number == null) number = string;
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
      public void toJson(JsonWriter writer, @Nullable BinaryAnnotation value) throws IOException {
        throw new UnsupportedOperationException();
      }
    };

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override  public Annotation fromJson(JsonReader reader) throws IOException {
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
    public void toJson(JsonWriter writer, @Nullable Annotation value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.builder().serviceName("");
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.skipValue();
          continue;
        }
        switch (nextName) {
          case "serviceName":
            result.serviceName(reader.nextString());
            break;
          case "ipv4":
          case "ipv6":
            result.parseIp(reader.nextString());
            break;
          case "port":
            result.port(reader.nextInt());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable Endpoint value) throws IOException {
      throw new UnsupportedOperationException();
    }
  }.nullSafe();

  static final JsonAdapter<DependencyLink> LINK_ADAPTER = new JsonAdapter<DependencyLink>() {
    @Override public DependencyLink fromJson(JsonReader reader) throws IOException {
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
          case "errorCount":
            result.errorCount(reader.nextLong());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable DependencyLink value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };
}
