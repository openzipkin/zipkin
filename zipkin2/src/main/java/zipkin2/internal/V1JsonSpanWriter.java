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

import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

import static zipkin2.internal.Buffer.asciiSizeInBytes;
import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;
import static zipkin2.internal.V2SpanWriter.writeAnnotation;

/** This type isn't thread-safe: it re-uses state to avoid re-allocations in conversion loops. */
// @Immutable
public final class V1JsonSpanWriter implements Buffer.Writer<Span> {
  final V2SpanConverter converter = V2SpanConverter.create();

  @Override
  public int sizeInBytes(Span value) {
    V1Span v1Span = converter.convert(value);

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
    if (v1Span.timestamp() != 0L) {
      sizeInBytes += 13; // ,"timestamp":
      sizeInBytes += asciiSizeInBytes(value.timestampAsLong());
    }
    if (v1Span.duration() != 0L) {
      sizeInBytes += 12; // ,"duration":
      sizeInBytes += asciiSizeInBytes(value.durationAsLong());
    }

    int annotationCount = v1Span.annotations().size();
    if (annotationCount > 0) {
      sizeInBytes += 17; // ,"annotations":[]
      if (annotationCount > 1) sizeInBytes += annotationCount - 1; // comma to join elements
      for (int i = 0; i < annotationCount; i++) {
        V1Annotation a = v1Span.annotations().get(i);
        sizeInBytes += V2SpanWriter.annotationSizeInBytes(a.timestamp(), a.value(), endpointSize);
      }
    }

    int binaryAnnotationCount = v1Span.binaryAnnotations().size();
    if (binaryAnnotationCount > 0) {
      sizeInBytes += 23; // ,"binaryAnnotations":[]
      if (binaryAnnotationCount > 1) sizeInBytes += binaryAnnotationCount - 1; // commas
      for (int i = 0; i < binaryAnnotationCount; ) {
        V1BinaryAnnotation a = v1Span.binaryAnnotations().get(i++);
        if (a.stringValue() != null) {
          sizeInBytes += binaryAnnotationSizeInBytes(a.key(), a.stringValue(), endpointSize);
        } else {
          sizeInBytes += 37; // {"key":"NN","value":true,"endpoint":}
          sizeInBytes += endpointSize(a.endpoint());
        }
      }
    }

    if (Boolean.TRUE.equals(value.debug())) sizeInBytes += 13; // ,"debug":true
    return ++sizeInBytes; // }
  }

  static int endpointSize(Endpoint endpoint) {
    if (endpoint == null) return 0;
    return V2SpanWriter.endpointSizeInBytes(endpoint, true);
  }

  @Override
  public void write(Span value, Buffer b) {
    byte[] endpointBytes = legacyEndpointBytes(value.localEndpoint());
    b.writeAscii("{\"traceId\":\"").writeAscii(value.traceId()).writeByte('"');
    if (value.parentId() != null) {
      b.writeAscii(",\"parentId\":\"").writeAscii(value.parentId()).writeByte('"');
    }
    b.writeAscii(",\"id\":\"").writeAscii(value.id()).writeByte('"');
    b.writeAscii(",\"name\":\"");
    if (value.name() != null) b.writeUtf8(jsonEscape(value.name()));
    b.writeByte('"');

    V1Span v1Span = converter.convert(value);
    if (v1Span.timestamp() != 0L) {
      b.writeAscii(",\"timestamp\":").writeAscii(value.timestampAsLong());
    }
    if (v1Span.duration() != 0L) {
      b.writeAscii(",\"duration\":").writeAscii(value.durationAsLong());
    }

    int annotationCount = v1Span.annotations().size();
    if (annotationCount > 0) {
      b.writeAscii(",\"annotations\":[");
      for (int i = 0; i < annotationCount; ) {
        V1Annotation a = v1Span.annotations().get(i++);
        writeAnnotation(a.timestamp(), a.value(), endpointBytes, b);
        if (i < annotationCount) b.writeByte(',');
      }
      b.writeByte(']');
    }
    int binaryAnnotationCount = v1Span.binaryAnnotations().size();
    if (binaryAnnotationCount > 0) {
      b.writeAscii(",\"binaryAnnotations\":[");
      for (int i = 0; i < binaryAnnotationCount; ) {
        V1BinaryAnnotation a = v1Span.binaryAnnotations().get(i++);
        if (a.stringValue() != null) {
          writeBinaryAnnotation(a.key(), a.stringValue(), endpointBytes, b);
        } else {
          b.writeAscii("{\"key\":\"").writeAscii(a.key());
          b.writeAscii("\",\"value\":true,\"endpoint\":");
          b.write(legacyEndpointBytes(a.endpoint()));
          b.writeByte('}');
        }
        if (i < binaryAnnotationCount) b.writeByte(',');
      }
      b.writeByte(']');
    }
    if (Boolean.TRUE.equals(value.debug())) {
      b.writeAscii(",\"debug\":true");
    }
    b.writeByte('}');
  }

  @Override
  public String toString() {
    return "Span";
  }

  static byte[] legacyEndpointBytes(@Nullable Endpoint localEndpoint) {
    if (localEndpoint == null) return null;
    Buffer buffer = new Buffer(V2SpanWriter.endpointSizeInBytes(localEndpoint, true));
    V2SpanWriter.writeEndpoint(localEndpoint, buffer, true);
    return buffer.toByteArray();
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
}
