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
package zipkin.internal.v2.codec;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import zipkin.internal.Buffer;
import zipkin.internal.JsonCodec;
import zipkin.internal.JsonCodec.JsonReaderAdapter;
import zipkin.internal.v2.Annotation;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;

import static zipkin.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.Buffer.jsonEscapedSizeInBytes;

/**
 * Internal type supporting codec operations in {@link Span}. Design rationale is the same as
 * {@link JsonCodec}.
 */
final class Span2JsonAdapters {

  static final class Span2Reader implements JsonReaderAdapter<Span> {
    Span.Builder builder;

    @Override public Span fromJson(JsonReader reader) throws IOException {
      if (builder == null) {
        builder = Span.newBuilder();
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
        } else if (reader.peek() == JsonToken.NULL) {
          reader.skipValue();
          continue;
        }

        // read any optional fields
        if (nextName.equals("parentId")) {
          builder.parentId(reader.nextString());
        } else if (nextName.equals("kind")) {
          builder.kind(Span.Kind.valueOf(reader.nextString()));
        } else if (nextName.equals("name")) {
          builder.name(reader.nextString());
        } else if (nextName.equals("timestamp")) {
          builder.timestamp(reader.nextLong());
        } else if (nextName.equals("duration")) {
          builder.duration(reader.nextLong());
        } else if (nextName.equals("localEndpoint")) {
          builder.localEndpoint(ENDPOINT_READER.fromJson(reader));
        } else if (nextName.equals("remoteEndpoint")) {
          builder.remoteEndpoint(ENDPOINT_READER.fromJson(reader));
        } else if (nextName.equals("annotations")) {
          reader.beginArray();
          while (reader.hasNext()) {
            reader.beginObject();
            Long timestamp = null;
            String value = null;
            while (reader.hasNext()) {
              nextName = reader.nextName();
              if (nextName.equals("timestamp")) {
                timestamp = reader.nextLong();
              } else if (nextName.equals("value")) {
                value = reader.nextString();
              } else {
                reader.skipValue();
              }
            }
            if (timestamp == null || value == null) {
              throw new MalformedJsonException("Incomplete annotation at " + reader.getPath());
            }
            reader.endObject();
            builder.addAnnotation(timestamp, value);
          }
          reader.endArray();
        } else if (nextName.equals("tags")) {
          reader.beginObject();
          while (reader.hasNext()) {
            String key = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
              throw new MalformedJsonException("No value at " + reader.getPath());
            }
            builder.putTag(key, reader.nextString());
          }
          reader.endObject();
        } else if (nextName.equals("debug")) {
          if (reader.nextBoolean()) builder.debug(true);
        } else if (nextName.equals("shared")) {
          if (reader.nextBoolean()) builder.shared(true);
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return builder.build();
    }

    @Override public String toString() {
      return "Span";
    }
  }

  static final JsonReaderAdapter<Endpoint> ENDPOINT_READER = reader -> {
    Endpoint.Builder result = Endpoint.newBuilder();
    reader.beginObject();
    boolean readField = false;
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if (reader.peek() == JsonToken.NULL) {
        reader.skipValue();
        continue;
      }
      if (nextName.equals("serviceName")) {
        result.serviceName(reader.nextString());
        readField = true;
      } else if (nextName.equals("ipv4") || nextName.equals("ipv6")) {
        result.parseIp(reader.nextString());
        readField = true;
      } else if (nextName.equals("port")) {
        result.port(reader.nextInt());
        readField = true;
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    if (!readField) throw new MalformedJsonException("Empty endpoint at " + reader.getPath());
    return result.build();
  };

  static final Buffer.Writer<Endpoint> ENDPOINT_WRITER = new Buffer.Writer<Endpoint>() {
    @Override public int sizeInBytes(Endpoint value) {
      int sizeInBytes = 1; // {
      if (value.serviceName() != null) {
        sizeInBytes += 16; // "serviceName":""
        sizeInBytes += jsonEscapedSizeInBytes(value.serviceName());
      }
      if (value.ipv4() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 9; // "ipv4":""
        sizeInBytes += value.ipv4().length();
      }
      if (value.ipv6() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 9; // "ipv6":""
        sizeInBytes += value.ipv6().length();
      }
      if (value.port() != null) {
        if (sizeInBytes != 1) sizeInBytes++; // ,
        sizeInBytes += 7; // "port":
        sizeInBytes += asciiSizeInBytes(value.port());
      }
      return ++sizeInBytes; // }
    }

    @Override public void write(Endpoint value, Buffer b) {
      b.writeByte('{');
      boolean wroteField = false;
      if (value.serviceName() != null) {
        b.writeAscii("\"serviceName\":\"");
        b.writeJsonEscaped(value.serviceName()).writeByte('"');
        wroteField = true;
      }
      if (value.ipv4() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv4\":\"");
        b.writeAscii(value.ipv4()).writeByte('"');
        wroteField = true;
      }
      if (value.ipv6() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv6\":\"");
        b.writeAscii(value.ipv6()).writeByte('"');
        wroteField = true;
      }
      if (value.port() != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"port\":").writeAscii(value.port());
      }
      b.writeByte('}');
    }
  };

  static final Buffer.Writer<Span> SPAN_WRITER = new Buffer.Writer<Span>() {
    @Override public int sizeInBytes(Span value) {
      int sizeInBytes = 13; // {"traceId":""
      sizeInBytes += value.traceId().length();
      if (value.parentId() != null) {
        sizeInBytes += 30; // ,"parentId":"0123456789abcdef"
      }
      sizeInBytes += 24; // ,"id":"0123456789abcdef"
      if (value.kind() != null) {
        sizeInBytes += 10; // ,"kind":""
        sizeInBytes += value.kind().name().length();
      }
      if (value.name() != null) {
        sizeInBytes += 10; // ,"name":""
        sizeInBytes += jsonEscapedSizeInBytes(value.name());
      }
      if (value.timestamp() != null) {
        sizeInBytes += 13; // ,"timestamp":
        sizeInBytes += asciiSizeInBytes(value.timestamp());
      }
      if (value.duration() != null) {
        sizeInBytes += 12; // ,"duration":
        sizeInBytes += asciiSizeInBytes(value.duration());
      }
      if (value.localEndpoint() != null) {
        sizeInBytes += 17; // ,"localEndpoint":
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.localEndpoint());
      }
      if (value.remoteEndpoint() != null) {
        sizeInBytes += 18; // ,"remoteEndpoint":
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.remoteEndpoint());
      }
      if (!value.annotations().isEmpty()) {
        sizeInBytes += 17; // ,"annotations":[]
        int length = value.annotations().size();
        if (length > 1) sizeInBytes += length - 1; // comma to join elements
        for (int i = 0; i < length; i++) {
          sizeInBytes += ANNOTATION_WRITER.sizeInBytes(value.annotations().get(i));
        }
      }
      if (!value.tags().isEmpty()) {
        sizeInBytes += 10; // ,"tags":{}
        int tagCount = value.tags().size();
        if (tagCount > 1) sizeInBytes += tagCount - 1; // comma to join elements
        for (Map.Entry<String, String> entry : value.tags().entrySet()) {
          sizeInBytes += 5; // "":""
          sizeInBytes += jsonEscapedSizeInBytes(entry.getKey());
          sizeInBytes += jsonEscapedSizeInBytes(entry.getValue());
        }
      }
      if (Boolean.TRUE.equals(value.debug())) {
        sizeInBytes += 13; // ,"debug":true
      }
      if (Boolean.TRUE.equals(value.shared())) {
        sizeInBytes += 14; // ,"shared":true
      }
      return ++sizeInBytes; // }
    }

    @Override public void write(Span value, Buffer b) {
      b.writeAscii("{\"traceId\":\"").writeAscii(value.traceId()).writeByte('"');
      if (value.parentId() != null) {
        b.writeAscii(",\"parentId\":\"").writeAscii(value.parentId()).writeByte('"');
      }
      b.writeAscii(",\"id\":\"").writeAscii(value.id()).writeByte('"');
      if (value.kind() != null) {
        b.writeAscii(",\"kind\":\"").writeJsonEscaped(value.kind().toString()).writeByte('"');
      }
      if (value.name() != null) {
        b.writeAscii(",\"name\":\"").writeJsonEscaped(value.name()).writeByte('"');
      }
      if (value.timestamp() != null) {
        b.writeAscii(",\"timestamp\":").writeAscii(value.timestamp());
      }
      if (value.duration() != null) {
        b.writeAscii(",\"duration\":").writeAscii(value.duration());
      }
      if (value.localEndpoint() != null) {
        b.writeAscii(",\"localEndpoint\":");
        ENDPOINT_WRITER.write(value.localEndpoint(), b);
      }
      if (value.remoteEndpoint() != null) {
        b.writeAscii(",\"remoteEndpoint\":");
        ENDPOINT_WRITER.write(value.remoteEndpoint(), b);
      }
      if (!value.annotations().isEmpty()) {
        b.writeAscii(",\"annotations\":");
        JsonCodec.writeList(ANNOTATION_WRITER, value.annotations(), b);
      }
      if (!value.tags().isEmpty()) {
        b.writeAscii(",\"tags\":{");
        Iterator<Map.Entry<String, String>> i = value.tags().entrySet().iterator();
        while (i.hasNext()) {
          Map.Entry<String, String> entry = i.next();
          b.writeByte('"').writeJsonEscaped(entry.getKey()).writeAscii("\":\"");
          b.writeJsonEscaped(entry.getValue()).writeByte('"');
          if (i.hasNext()) b.writeByte(',');
        }
        b.writeByte('}');
      }
      if (Boolean.TRUE.equals(value.debug())) {
        b.writeAscii(",\"debug\":true");
      }
      if (Boolean.TRUE.equals(value.shared())) {
        b.writeAscii(",\"shared\":true");
      }
      b.writeByte('}');
    }

    @Override public String toString() {
      return "Span";
    }
  };

  static final Buffer.Writer<Annotation> ANNOTATION_WRITER = new Buffer.Writer<Annotation>() {
    @Override public int sizeInBytes(Annotation value) {
      int sizeInBytes = 25; // {"timestamp":,"value":""}
      sizeInBytes += asciiSizeInBytes(value.timestamp());
      sizeInBytes += jsonEscapedSizeInBytes(value.value());
      return sizeInBytes;
    }

    @Override public void write(Annotation value, Buffer b) {
      b.writeAscii("{\"timestamp\":").writeAscii(value.timestamp());
      b.writeAscii(",\"value\":\"").writeJsonEscaped(value.value()).writeAscii("\"}");
    }
  };

  static final class Span2ListReader implements JsonReaderAdapter<List<Span>> {
    Span2Reader spanReader;

    @Override public List<Span> fromJson(JsonReader reader) throws IOException {
      reader.beginArray();
      if (!reader.hasNext()) {
        reader.endArray();
        return Collections.emptyList();
      }
      List<Span> result = new LinkedList<>(); // because we don't know how long it will be
      if (spanReader == null) spanReader = new Span2Reader();
      while (reader.hasNext()) result.add(spanReader.fromJson(reader));
      reader.endArray();
      return result;
    }

    @Override public String toString() {
      return "List<Span>";
    }
  }
}
