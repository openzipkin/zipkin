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
import java.util.Collection;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.JsonCodec.JsonReaderAdapter;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static zipkin2.internal.JsonCodec.exceptionReading;
import static zipkin2.internal.V2SpanReader.ENDPOINT_READER;

public final class V1JsonSpanReader implements JsonReaderAdapter<V1Span> {

  V1Span.Builder builder;

  public boolean readList(ReadBuffer buffer, Collection<Span> out) {
    if (buffer.available() == 0) return false;
    V1SpanConverter converter = V1SpanConverter.create();
    JsonReader reader = new JsonReader(buffer);
    try {
      reader.beginArray();
      if (!reader.hasNext()) return false;
      while (reader.hasNext()) {
        V1Span result = fromJson(reader);
        converter.convert(result, out);
      }
      reader.endArray();
      return true;
    } catch (Exception e) {
      throw exceptionReading("List<Span>", e);
    }
  }

  @Override public V1Span fromJson(JsonReader reader) throws IOException {
    if (builder == null) {
      builder = V1Span.newBuilder();
    } else {
      builder.clear();
    }
    reader.beginObject();
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if (nextName.equals("traceId")) {
        builder.traceId(reader.nextString());
        continue;
      } else if (nextName.equals("id")) {
        builder.id(reader.nextString());
        continue;
      } else if (reader.peekNull()) {
        reader.skipValue();
        continue;
      }

      // read any optional fields
      if (nextName.equals("name")) {
        builder.name(reader.nextString());
      } else if (nextName.equals("parentId")) {
        builder.parentId(reader.nextString());
      } else if (nextName.equals("timestamp")) {
        builder.timestamp(reader.nextLong());
      } else if (nextName.equals("duration")) {
        builder.duration(reader.nextLong());
      } else if (nextName.equals("annotations")) {
        reader.beginArray();
        while (reader.hasNext()) readAnnotation(reader);
        reader.endArray();
      } else if (nextName.equals("binaryAnnotations")) {
        reader.beginArray();
        while (reader.hasNext()) readBinaryAnnotation(reader);
        reader.endArray();
      } else if (nextName.equals("debug")) {
        if (reader.nextBoolean()) builder.debug(true);
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    return builder.build();
  }

  void readAnnotation(JsonReader reader) throws IOException {
    String nextName;
    reader.beginObject();
    Long timestamp = null;
    String value = null;
    Endpoint endpoint = null;
    while (reader.hasNext()) {
      nextName = reader.nextName();
      if (nextName.equals("timestamp")) {
        timestamp = reader.nextLong();
      } else if (nextName.equals("value")) {
        value = reader.nextString();
      } else if (nextName.equals("endpoint") && !reader.peekNull()) {
        endpoint = ENDPOINT_READER.fromJson(reader);
      } else {
        reader.skipValue();
      }
    }
    if (timestamp == null || value == null) {
      throw new IllegalArgumentException("Incomplete annotation at " + reader.getPath());
    }
    reader.endObject();
    builder.addAnnotation(timestamp, value, endpoint);
  }

  @Override public String toString() {
    return "Span";
  }

  void readBinaryAnnotation(JsonReader reader) throws IOException {
    String key = null;
    Endpoint endpoint = null;
    Boolean booleanValue = null;
    String stringValue = null;

    reader.beginObject();
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if (reader.peekNull()) {
        reader.skipValue();
        continue;
      }

      if (nextName.equals("key")) {
        key = reader.nextString();
      } else if (nextName.equals("value")) {
        if (reader.peekString()) {
          stringValue = reader.nextString();
        } else if (reader.peekBoolean()) {
          booleanValue = reader.nextBoolean();
        } else {
          reader.skipValue();
        }
      } else if (nextName.equals("endpoint")) {
        endpoint = ENDPOINT_READER.fromJson(reader);
      } else {
        reader.skipValue();
      }
    }

    if (key == null) {
      throw new IllegalArgumentException("No key at " + reader.getPath());
    }
    reader.endObject();

    if (stringValue != null) {
      builder.addBinaryAnnotation(key, stringValue, endpoint);
    } else if (booleanValue != null && booleanValue && endpoint != null) {
      if (key.equals("sa") || key.equals("ca") || key.equals("ma")) {
        builder.addBinaryAnnotation(key, endpoint);
      }
    }
  }
}
