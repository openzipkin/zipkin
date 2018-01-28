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

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static zipkin2.internal.Buffer.asciiSizeInBytes;
import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;

//@Immutable
public final class V1SpanWriter implements Buffer.Writer<Span> {
  @Override public int sizeInBytes(Span value) {
    Parsed parsed = parse(value);

    Integer endpointSize;
    if (value.localEndpoint() != null) {
      endpointSize = V2SpanWriter.endpointSizeInBytes(value.localEndpoint());
      if (value.localServiceName() == null) {
        endpointSize += 17; // "serviceName":"",
      }
    } else {
      endpointSize = null;
    }
    int sizeInBytes = 13; // {"traceId":""
    sizeInBytes += value.traceId().length();
    if (value.parentId() != null) {
      sizeInBytes += 30; // ,"parentId":"0123456789abcdef"
    }
    sizeInBytes += 24; // ,"id":"0123456789abcdef"
    sizeInBytes += 10; // ,"name":""
    if (value.name() != null) {
      sizeInBytes += jsonEscapedSizeInBytes(value.name());
    }
    if (Boolean.TRUE.equals(value.shared()) && "sr".equals(parsed.begin)) {
      // don't report server-side timestamp on shared or incomplete spans
    } else {
      if (value.timestampAsLong() != 0L) {
        sizeInBytes += 13; // ,"timestamp":
        sizeInBytes += asciiSizeInBytes(value.timestampAsLong());
      }
      if (value.durationAsLong() != 0L) {
        sizeInBytes += 12; // ,"duration":
        sizeInBytes += asciiSizeInBytes(value.durationAsLong());
      }
    }

    int annotationCount = value.annotations().size();

    if (parsed.startTs != 0L && parsed.begin != null) {
      annotationCount++;
      sizeInBytes += coreAnnotationSizeInBytes(parsed.startTs, endpointSize);
    }

    if (parsed.endTs != 0L && parsed.end != null) {
      annotationCount++;
      sizeInBytes += coreAnnotationSizeInBytes(parsed.endTs, endpointSize);
    }

    if (annotationCount > 0) {
      sizeInBytes += 17; // ,"annotations":[]
      if (annotationCount > 1) sizeInBytes += annotationCount - 1; // comma to join elements
      for (int i = 0, length = value.annotations().size(); i < length; i++) {
        sizeInBytes += V2SpanWriter.annotationSizeInBytes(value.annotations().get(i), endpointSize);
      }
    }

    int binaryAnnotationCount = value.tags().size();

    if (parsed.remoteEndpointType != null && value.remoteEndpoint() != null) {
      binaryAnnotationCount++;
      sizeInBytes += 37; // {"key":"NN","value":true,"endpoint":}
      sizeInBytes += V2SpanWriter.endpointSizeInBytes(value.remoteEndpoint());
      if (value.remoteServiceName() == null) {
        sizeInBytes += 17; // "serviceName":"",
      }
    }

    if (binaryAnnotationCount > 0) {
      sizeInBytes += 23; // ,"binaryAnnotations":[]
      if (binaryAnnotationCount > 1) {
        sizeInBytes += binaryAnnotationCount - 1; // comma to join elements
      }
      for (Map.Entry<String, String> tag : value.tags().entrySet()) {
        sizeInBytes += binaryAnnotationSizeInBytes(tag.getKey(), tag.getValue(), endpointSize);
      }
    }
    if (Boolean.TRUE.equals(value.debug())) {
      sizeInBytes += 13; // ,"debug":true
    }
    return ++sizeInBytes; // }
  }

  @Override public void write(Span value, Buffer b) {
    Parsed parsed = parse(value);
    byte[] endpointBytes = legacyEndpointBytes(value.localEndpoint());
    b.writeAscii("{\"traceId\":\"").writeAscii(value.traceId()).writeByte('"');
    if (value.parentId() != null) {
      b.writeAscii(",\"parentId\":\"").writeAscii(value.parentId()).writeByte('"');
    }
    b.writeAscii(",\"id\":\"").writeAscii(value.id()).writeByte('"');
    b.writeAscii(",\"name\":\"");
    if (value.name() != null) b.writeUtf8(jsonEscape(value.name()));
    b.writeByte('"');

    if (Boolean.TRUE.equals(value.shared()) && "sr".equals(parsed.begin)) {
      // don't report server-side timestamp on shared or incomplete spans
    } else {
      if (value.timestampAsLong() != 0L) {
        b.writeAscii(",\"timestamp\":").writeAscii(value.timestampAsLong());
      }
      if (value.durationAsLong() != 0L) {
        b.writeAscii(",\"duration\":").writeAscii(value.durationAsLong());
      }
    }

    int annotationCount = value.annotations().size();
    boolean beginAnnotation = parsed.startTs != 0L && parsed.begin != null;
    boolean endAnnotation = parsed.endTs != 0L && parsed.end != null;
    if (annotationCount > 0 || beginAnnotation || endAnnotation) {
      int length = value.annotations().size();
      b.writeAscii(",\"annotations\":[");
      if (beginAnnotation) {
        V2SpanWriter.writeAnnotation(Annotation.create(parsed.startTs, parsed.begin), endpointBytes,
          b);
        if (length > 0) b.writeByte(',');
      }
      for (int i = 0; i < length; ) {
        V2SpanWriter.writeAnnotation(value.annotations().get(i++), endpointBytes, b);
        if (i < length) b.writeByte(',');
      }
      if (endAnnotation) {
        b.writeByte(',');
        V2SpanWriter.writeAnnotation(Annotation.create(parsed.endTs, parsed.end), endpointBytes, b);
      }
      b.writeByte(']');
    }
    int binaryAnnotationCount = value.tags().size();

    boolean hasRemoteEndpoint = parsed.remoteEndpointType != null && value.remoteEndpoint() != null;
    if (hasRemoteEndpoint) binaryAnnotationCount++;
    if (binaryAnnotationCount > 0) {
      b.writeAscii(",\"binaryAnnotations\":[");
      Iterator<Map.Entry<String, String>> i = value.tags().entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry<String, String> entry = i.next();
        writeBinaryAnnotation(entry.getKey(), entry.getValue(), endpointBytes, b);
        if (i.hasNext()) b.writeByte(',');
      }
      if (hasRemoteEndpoint) {
        if (!value.tags().isEmpty()) b.writeByte(',');
        b.writeAscii("{\"key\":\"").writeAscii(parsed.remoteEndpointType);
        b.writeAscii("\",\"value\":true,\"endpoint\":");
        b.write(legacyEndpointBytes(value.remoteEndpoint()));
        b.writeByte('}');
      }
      b.writeByte(']');
    }
    if (Boolean.TRUE.equals(value.debug())) {
      b.writeAscii(",\"debug\":true");
    }
    b.writeByte('}');
  }

  @Override public String toString() {
    return "Span";
  }

  static final byte[] EMPTY_SERVICE = "{\"serviceName\":\"\"".getBytes(Charset.forName("UTF-8"));

  static byte[] legacyEndpointBytes(@Nullable Endpoint localEndpoint) {
    if (localEndpoint == null) return null;
    Buffer buffer = new Buffer(V2SpanWriter.endpointSizeInBytes(localEndpoint));
    V2SpanWriter.writeEndpoint(localEndpoint, buffer);
    byte[] endpointBytes = buffer.toByteArray();
    if (localEndpoint.serviceName() != null) return endpointBytes;
    byte[] newSpanBytes = new byte[EMPTY_SERVICE.length + endpointBytes.length];
    System.arraycopy(EMPTY_SERVICE, 0, newSpanBytes, 0, EMPTY_SERVICE.length);
    newSpanBytes[EMPTY_SERVICE.length] = ',';
    System.arraycopy(endpointBytes, 1, newSpanBytes, 18, endpointBytes.length - 1);
    return newSpanBytes;
  }

  static int binaryAnnotationSizeInBytes(String key, String value, @Nullable Integer endpointSize) {
    int sizeInBytes = 21; // {"key":"","value":""}
    sizeInBytes += jsonEscapedSizeInBytes(key);
    sizeInBytes += jsonEscapedSizeInBytes(value);
    if (endpointSize != null) {
      sizeInBytes += 12; // ,"endpoint":
      sizeInBytes += endpointSize;
    }
    return sizeInBytes;
  }

  static void writeBinaryAnnotation(String key, String value, @Nullable byte[] endpoint, Buffer b) {
    b.writeAscii("{\"key\":\"").writeUtf8(jsonEscape(key));
    b.writeAscii("\",\"value\":\"").writeUtf8(jsonEscape(value)).writeByte('"');
    if (endpoint != null) b.writeAscii(",\"endpoint\":").write(endpoint);
    b.writeAscii("}");
  }

  static class Parsed {
    long startTs, endTs;
    String begin = null, end = null;
    String remoteEndpointType = null;
  }

  static Parsed parse(Span in) {
    Parsed parsed = new Parsed();
    parsed.startTs = in.timestampAsLong();
    parsed.endTs = parsed.startTs != 0L && in.durationAsLong() != 0L
      ? parsed.startTs + in.durationAsLong() : 0L;

    if (in.kind() != null) {
      switch (in.kind()) {
        case CLIENT:
          parsed.remoteEndpointType = "sa";
          parsed.begin = "cs";
          parsed.end = "cr";
          break;
        case SERVER:
          parsed.remoteEndpointType = "ca";
          parsed.begin = "sr";
          parsed.end = "ss";
          break;
        case PRODUCER:
          parsed.remoteEndpointType = "ma";
          parsed.begin = "ms";
          parsed.end = "ws";
          break;
        case CONSUMER:
          parsed.remoteEndpointType = "ma";
          if (parsed.endTs != 0L) {
            parsed.begin = "wr";
            parsed.end = "mr";
          } else {
            parsed.begin = "mr";
          }
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
    }
    return parsed;
  }

  static int coreAnnotationSizeInBytes(long timestamp, @Nullable Integer endpointSizeInBytes) {
    int sizeInBytes = 27; // {"timestamp":,"value":"??"}
    sizeInBytes += asciiSizeInBytes(timestamp);
    if (endpointSizeInBytes != null) {
      sizeInBytes += 12; // ,"endpoint":
      sizeInBytes += endpointSizeInBytes;
    }
    return sizeInBytes;
  }
}
