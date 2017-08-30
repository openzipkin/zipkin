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
import zipkin.Annotation;
import zipkin.Endpoint;
import zipkin.internal.Buffer;
import zipkin.internal.JsonCodec;
import zipkin.internal.JsonCodec.JsonReaderAdapter;
import zipkin.internal.v2.Span;

import static zipkin.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.Buffer.ipv6SizeInBytes;
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
        builder = Span.builder();
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
    Endpoint.Builder result = Endpoint.builder().serviceName("");
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
      int sizeInBytes = 1; // start curly-brace
      if (!value.serviceName.isEmpty()) {
        sizeInBytes += "\"serviceName\":\"".length();
        sizeInBytes += jsonEscapedSizeInBytes(value.serviceName) + 1; // for end quote
      }
      if (value.ipv4 != 0) {
        if (sizeInBytes != 1) sizeInBytes++;// comma
        sizeInBytes += "\"ipv4\":\"".length();
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 24 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 16 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 8 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 & 0xff) + 1; // for end quote
      }
      if (value.ipv6 != null) {
        if (sizeInBytes != 1) sizeInBytes++;// comma
        sizeInBytes += "\"ipv6\":\"".length() + ipv6SizeInBytes(value.ipv6) + 1;
      }
      if (value.port != null && value.port != 0) {
        if (sizeInBytes != 1) sizeInBytes++;// comma
        sizeInBytes += "\"port\":".length() + asciiSizeInBytes(value.port & 0xffff);
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Endpoint value, Buffer b) {
      b.writeByte('{');
      boolean wroteField = false;
      if (!value.serviceName.isEmpty()) {
        b.writeAscii("\"serviceName\":\"");
        b.writeJsonEscaped(value.serviceName).writeByte('"');
        wroteField = true;
      }
      if (value.ipv4 != 0) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv4\":\"");
        b.writeAscii(value.ipv4 >> 24 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 >> 16 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 >> 8 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 & 0xff).writeByte('"');
        wroteField = true;
      }
      if (value.ipv6 != null) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"ipv6\":\"").writeIpV6(value.ipv6).writeByte('"');
        wroteField = true;
      }
      if (value.port != null && value.port != 0) {
        if (wroteField) b.writeByte(',');
        b.writeAscii("\"port\":").writeAscii(value.port & 0xffff);
      }
      b.writeByte('}');
    }
  };

  static final Buffer.Writer<Span> SPAN_WRITER = new Buffer.Writer<Span>() {
    @Override public int sizeInBytes(Span value) {
      int sizeInBytes = 0;
      if (value.traceIdHigh() != 0) sizeInBytes += 16;
      sizeInBytes += "{\"traceId\":\"".length() + 16 + 1;
      if (value.parentId() != null) {
        sizeInBytes += ",\"parentId\":\"".length() + 16 + 1;
      }
      sizeInBytes += ",\"id\":\"".length() + 16 + 1;
      if (value.kind() != null) {
        sizeInBytes += ",\"kind\":\"".length();
        sizeInBytes += value.kind().toString().length() + 1;
      }
      if (value.name() != null) {
        sizeInBytes += ",\"name\":\"".length();
        sizeInBytes += jsonEscapedSizeInBytes(value.name()) + 1;
      }
      if (value.timestamp() != null) {
        sizeInBytes += ",\"timestamp\":".length();
        sizeInBytes += asciiSizeInBytes(value.timestamp());
      }
      if (value.duration() != null) {
        sizeInBytes += ",\"duration\":".length();
        sizeInBytes += asciiSizeInBytes(value.duration());
      }
      if (value.localEndpoint() != null) {
        sizeInBytes += ",\"localEndpoint\":".length();
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.localEndpoint());
      }
      if (value.remoteEndpoint() != null) {
        sizeInBytes += ",\"remoteEndpoint\":".length();
        sizeInBytes += ENDPOINT_WRITER.sizeInBytes(value.remoteEndpoint());
      }
      if (!value.annotations().isEmpty()) {
        sizeInBytes += ",\"annotations\":".length();
        int length = value.annotations().size();
        sizeInBytes += 2; // brackets
        if (length > 1) sizeInBytes += length - 1; // comma to join elements
        for (int i = 0; i < length; i++) {
          sizeInBytes += ANNOTATION_WRITER.sizeInBytes(value.annotations().get(i));
        }
      }
      if (!value.tags().isEmpty()) {
        sizeInBytes += ",\"tags\":".length();
        sizeInBytes += 2; // curly braces
        int tagCount = value.tags().size();
        if (tagCount > 1) sizeInBytes += tagCount - 1; // comma to join elements
        for (Map.Entry<String, String> entry : value.tags().entrySet()) {
          sizeInBytes += 5; // 4 quotes and a colon
          sizeInBytes += jsonEscapedSizeInBytes(entry.getKey());
          sizeInBytes += jsonEscapedSizeInBytes(entry.getValue());
        }
      }
      if (Boolean.TRUE.equals(value.debug())) {
        sizeInBytes += ",\"debug\":true".length();
      }
      if (Boolean.TRUE.equals(value.shared())) {
        sizeInBytes += ",\"shared\":true".length();
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Span value, Buffer b) {
      b.writeAscii("{\"traceId\":\"");
      if (value.traceIdHigh() != 0) {
        b.writeLowerHex(value.traceIdHigh());
      }
      b.writeLowerHex(value.traceId()).writeByte('"');
      if (value.parentId() != null) {
        b.writeAscii(",\"parentId\":\"").writeLowerHex(value.parentId()).writeByte('"');
      }
      b.writeAscii(",\"id\":\"").writeLowerHex(value.id()).writeByte('"');
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
      int sizeInBytes = 0;
      sizeInBytes += "{\"timestamp\":".length() + asciiSizeInBytes(value.timestamp);
      sizeInBytes += ",\"value\":\"".length() + jsonEscapedSizeInBytes(value.value) + 1;
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Annotation value, Buffer b) {
      b.writeAscii("{\"timestamp\":").writeAscii(value.timestamp);
      b.writeAscii(",\"value\":\"").writeJsonEscaped(value.value).writeAscii("\"}");
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
