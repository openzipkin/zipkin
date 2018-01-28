/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import java.util.Iterator;
import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static zipkin2.internal.Buffer.asciiSizeInBytes;
import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;

//@Immutable
public final class V2SpanWriter implements Buffer.Writer<Span> {
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
    if (value.timestampAsLong() != 0L) {
      sizeInBytes += 13; // ,"timestamp":
      sizeInBytes += asciiSizeInBytes(value.timestampAsLong());
    }
    if (value.durationAsLong() != 0L) {
      sizeInBytes += 12; // ,"duration":
      sizeInBytes += asciiSizeInBytes(value.durationAsLong());
    }
    if (value.localEndpoint() != null) {
      sizeInBytes += 17; // ,"localEndpoint":
      sizeInBytes += endpointSizeInBytes(value.localEndpoint());
    }
    if (value.remoteEndpoint() != null) {
      sizeInBytes += 18; // ,"remoteEndpoint":
      sizeInBytes += endpointSizeInBytes(value.remoteEndpoint());
    }
    if (!value.annotations().isEmpty()) {
      sizeInBytes += 17; // ,"annotations":[]
      int length = value.annotations().size();
      if (length > 1) sizeInBytes += length - 1; // comma to join elements
      for (int i = 0; i < length; i++) {
        sizeInBytes += annotationSizeInBytes(value.annotations().get(i), null);
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
      b.writeAscii(",\"kind\":\"").writeAscii(value.kind().toString()).writeByte('"');
    }
    if (value.name() != null) {
      b.writeAscii(",\"name\":\"").writeUtf8(jsonEscape(value.name())).writeByte('"');
    }
    if (value.timestampAsLong() != 0L) {
      b.writeAscii(",\"timestamp\":").writeAscii(value.timestampAsLong());
    }
    if (value.durationAsLong() != 0L) {
      b.writeAscii(",\"duration\":").writeAscii(value.durationAsLong());
    }
    if (value.localEndpoint() != null) {
      b.writeAscii(",\"localEndpoint\":");
      writeEndpoint(value.localEndpoint(), b);
    }
    if (value.remoteEndpoint() != null) {
      b.writeAscii(",\"remoteEndpoint\":");
      writeEndpoint(value.remoteEndpoint(), b);
    }
    if (!value.annotations().isEmpty()) {
      b.writeAscii(",\"annotations\":");
      b.writeByte('[');
      for (int i = 0, length = value.annotations().size(); i < length; ) {
        writeAnnotation(value.annotations().get(i++), null, b);
        if (i < length) b.writeByte(',');
      }
      b.writeByte(']');
    }
    if (!value.tags().isEmpty()) {
      b.writeAscii(",\"tags\":{");
      Iterator<Map.Entry<String, String>> i = value.tags().entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry<String, String> entry = i.next();
        b.writeByte('"').writeUtf8(jsonEscape(entry.getKey())).writeAscii("\":\"");
        b.writeUtf8(jsonEscape(entry.getValue())).writeByte('"');
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

  static int endpointSizeInBytes(Endpoint value) {
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

  static void writeEndpoint(Endpoint value, Buffer b) {
    b.writeByte('{');
    boolean wroteField = false;
    if (value.serviceName() != null) {
      b.writeAscii("\"serviceName\":\"");
      b.writeUtf8(jsonEscape(value.serviceName())).writeByte('"');
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

  static int annotationSizeInBytes(Annotation value, @Nullable Integer endpointSizeInBytes) {
    int sizeInBytes = 25; // {"timestamp":,"value":""}
    sizeInBytes += asciiSizeInBytes(value.timestamp());
    sizeInBytes += jsonEscapedSizeInBytes(value.value());
    if (endpointSizeInBytes != null) {
      sizeInBytes += 12; // ,"endpoint":
      sizeInBytes += endpointSizeInBytes;
    }
    return sizeInBytes;
  }

  static void writeAnnotation(Annotation value, @Nullable byte[] endpointBytes, Buffer b) {
    b.writeAscii("{\"timestamp\":").writeAscii(value.timestamp());
    b.writeAscii(",\"value\":\"").writeUtf8(jsonEscape(value.value())).writeByte('"');
    if (endpointBytes != null) b.writeAscii(",\"endpoint\":").write(endpointBytes);
    b.writeByte('}');
  }
}
