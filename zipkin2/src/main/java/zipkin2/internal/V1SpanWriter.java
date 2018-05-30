/*
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

// @Immutable
public final class V1SpanWriter implements Buffer.Writer<Span> {
  @Override public int sizeInBytes(Span value) {
    V1Metadata v1Metadata = V1Metadata.parse(value);

    int endpointSize = endpointSize(value.localEndpoint());

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
    if (!Boolean.TRUE.equals(value.shared())) {
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

    if (v1Metadata.startTs != 0L && v1Metadata.begin != null) {
      annotationCount++;
      sizeInBytes += coreAnnotationSizeInBytes(v1Metadata.startTs, endpointSize);
    }

    if (v1Metadata.endTs != 0L && v1Metadata.end != null) {
      annotationCount++;
      sizeInBytes += coreAnnotationSizeInBytes(v1Metadata.endTs, endpointSize);
    }

    if (annotationCount > 0) {
      sizeInBytes += 17; // ,"annotations":[]
      if (annotationCount > 1) sizeInBytes += annotationCount - 1; // comma to join elements
      for (int i = 0, length = value.annotations().size(); i < length; i++) {
        sizeInBytes += V2SpanWriter.annotationSizeInBytes(value.annotations().get(i), endpointSize);
      }
    }

    int binaryAnnotationCount = value.tags().size();

    boolean writeLocalComponent =
      annotationCount == 0 && endpointSize != 0 && binaryAnnotationCount == 0;
    if (writeLocalComponent) {
      binaryAnnotationCount++;
      sizeInBytes += 35 + endpointSize; // {"key":"lc","value":"","endpoint":}
    }

    if (v1Metadata.remoteEndpointType != null && value.remoteEndpoint() != null) {
      binaryAnnotationCount++;
      sizeInBytes += 37; // {"key":"NN","value":true,"endpoint":}
      sizeInBytes += endpointSize(value.remoteEndpoint());
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

  static int endpointSize(Endpoint endpoint) {
    if (endpoint == null) return 0;
    int endpointSize = V2SpanWriter.endpointSizeInBytes(endpoint);
    if (endpoint.serviceName() == null) {
      endpointSize += 17; // "serviceName":"",
    }
    return endpointSize;
  }

  @Override public void write(Span value, Buffer b) {
    V1Metadata v1Metadata = V1Metadata.parse(value);
    byte[] endpointBytes = legacyEndpointBytes(value.localEndpoint());
    b.writeAscii("{\"traceId\":\"").writeAscii(value.traceId()).writeByte('"');
    if (value.parentId() != null) {
      b.writeAscii(",\"parentId\":\"").writeAscii(value.parentId()).writeByte('"');
    }
    b.writeAscii(",\"id\":\"").writeAscii(value.id()).writeByte('"');
    b.writeAscii(",\"name\":\"");
    if (value.name() != null) b.writeUtf8(jsonEscape(value.name()));
    b.writeByte('"');

    // Don't report timestamp and duration on shared spans (should be server, but not necessarily)
    if (!Boolean.TRUE.equals(value.shared())) {
      if (value.timestampAsLong() != 0L) {
        b.writeAscii(",\"timestamp\":").writeAscii(value.timestampAsLong());
      }
      if (value.durationAsLong() != 0L) {
        b.writeAscii(",\"duration\":").writeAscii(value.durationAsLong());
      }
    }

    int annotationCount = value.annotations().size();
    boolean beginAnnotation = v1Metadata.startTs != 0L && v1Metadata.begin != null;
    boolean endAnnotation = v1Metadata.endTs != 0L && v1Metadata.end != null;
    boolean writeAnnotations = annotationCount > 0 || beginAnnotation || endAnnotation;
    if (writeAnnotations) {
      int length = value.annotations().size();
      b.writeAscii(",\"annotations\":[");
      if (beginAnnotation) {
        V2SpanWriter.writeAnnotation(
            Annotation.create(v1Metadata.startTs, v1Metadata.begin), endpointBytes, b);
        if (length > 0) b.writeByte(',');
      }
      for (int i = 0; i < length; ) {
        V2SpanWriter.writeAnnotation(value.annotations().get(i++), endpointBytes, b);
        if (i < length) b.writeByte(',');
      }
      if (endAnnotation) {
        b.writeByte(',');
        V2SpanWriter.writeAnnotation(
            Annotation.create(v1Metadata.endTs, v1Metadata.end), endpointBytes, b);
      }
      b.writeByte(']');
    }
    int binaryAnnotationCount = value.tags().size();

    boolean writeLocalComponent =
      !writeAnnotations && endpointBytes != null && binaryAnnotationCount == 0;
    if (writeLocalComponent) binaryAnnotationCount++;

    boolean hasRemoteEndpoint =
        v1Metadata.remoteEndpointType != null && value.remoteEndpoint() != null;
    if (hasRemoteEndpoint) binaryAnnotationCount++;

    if (binaryAnnotationCount > 0) {
      b.writeAscii(",\"binaryAnnotations\":[");
      Iterator<Map.Entry<String, String>> i = value.tags().entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry<String, String> entry = i.next();
        writeBinaryAnnotation(entry.getKey(), entry.getValue(), endpointBytes, b);
        if (i.hasNext()) b.writeByte(',');
      }
      // write an empty "lc" annotation to avoid missing the localEndpoint in an in-process span
      if (writeLocalComponent) {
        if (!value.tags().isEmpty()) b.writeByte(',');
        writeBinaryAnnotation("lc", "", endpointBytes, b);
      }
      if (hasRemoteEndpoint) {
        if (writeLocalComponent || !value.tags().isEmpty()) b.writeByte(',');
        b.writeAscii("{\"key\":\"").writeAscii(v1Metadata.remoteEndpointType);
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

  static int binaryAnnotationSizeInBytes(String key, String value, int endpointSize) {
    int sizeInBytes = 21; // {"key":"","value":""}
    sizeInBytes += jsonEscapedSizeInBytes(key);
    sizeInBytes += jsonEscapedSizeInBytes(value);
    if (endpointSize != 0) {
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

  static int coreAnnotationSizeInBytes(long timestamp, int endpointSizeInBytes) {
    int sizeInBytes = 27; // {"timestamp":,"value":"??"}
    sizeInBytes += asciiSizeInBytes(timestamp);
    if (endpointSizeInBytes != 0) {
      sizeInBytes += 12; // ,"endpoint":
      sizeInBytes += endpointSizeInBytes;
    }
    return sizeInBytes;
  }
}
