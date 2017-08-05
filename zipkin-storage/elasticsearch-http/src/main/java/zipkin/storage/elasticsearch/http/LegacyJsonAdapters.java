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
import okio.Buffer;
import okio.ByteString;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;
import zipkin.internal.Util;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.lowerHexToUnsignedLong;

final class LegacyJsonAdapters {
  static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override
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
            result.id(Util.lowerHexToUnsignedLong(reader.nextString()));
            break;
          case "parentId":
            result.parentId(Util.lowerHexToUnsignedLong(reader.nextString()));
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
              result.addAnnotation(JsonAdapters.ANNOTATION_ADAPTER.fromJson(reader));
            }
            reader.endArray();
            break;
          case "binaryAnnotations":
            reader.beginArray();
            while (reader.hasNext()) {
              result.addBinaryAnnotation(JsonAdapters.BINARY_ANNOTATION_ADAPTER.fromJson(reader));
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
      throw new UnsupportedOperationException();
    }
  };
}

