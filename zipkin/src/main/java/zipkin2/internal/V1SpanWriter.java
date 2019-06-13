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

import zipkin2.Endpoint;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;

import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;
import static zipkin2.internal.V2SpanWriter.endpointSizeInBytes;
import static zipkin2.internal.V2SpanWriter.writeAnnotation;
import static zipkin2.internal.WriteBuffer.asciiSizeInBytes;

/** This type is only used to backport the v1 read api as it returns v1 json. */
// @Immutable
public final class V1SpanWriter implements WriteBuffer.Writer<V1Span> {

  @Override public int sizeInBytes(V1Span value) {
    int sizeInBytes = 29; // {"traceId":"xxxxxxxxxxxxxxxx"
    if (value.traceIdHigh() != 0L) sizeInBytes += 16;
    if (value.parentId() != 0L) {
      sizeInBytes += 30; // ,"parentId":"0123456789abcdef"
    }
    sizeInBytes += 24; // ,"id":"0123456789abcdef"
    sizeInBytes += 10; // ,"name":""
    if (value.name() != null) {
      sizeInBytes += jsonEscapedSizeInBytes(value.name());
    }
    if (value.timestamp() != 0L) {
      sizeInBytes += 13; // ,"timestamp":
      sizeInBytes += asciiSizeInBytes(value.timestamp());
    }
    if (value.duration() != 0L) {
      sizeInBytes += 12; // ,"duration":
      sizeInBytes += asciiSizeInBytes(value.duration());
    }

    int annotationCount = value.annotations().size();
    Endpoint lastEndpoint = null;
    int lastEndpointSize = 0;
    if (annotationCount > 0) {
      sizeInBytes += 17; // ,"annotations":[]
      if (annotationCount > 1) sizeInBytes += annotationCount - 1; // comma to join elements
      for (int i = 0; i < annotationCount; i++) {
        V1Annotation a = value.annotations().get(i);
        Endpoint endpoint = a.endpoint();
        int endpointSize;
        if (endpoint == null) {
          endpointSize = 0;
        } else if (endpoint.equals(lastEndpoint)) {
          endpointSize = lastEndpointSize;
        } else {
          lastEndpoint = endpoint;
          endpointSize = lastEndpointSize = endpointSizeInBytes(endpoint, true);
        }
        sizeInBytes += V2SpanWriter.annotationSizeInBytes(a.timestamp(), a.value(), endpointSize);
      }
    }

    int binaryAnnotationCount = value.binaryAnnotations().size();
    if (binaryAnnotationCount > 0) {
      sizeInBytes += 23; // ,"binaryAnnotations":[]
      if (binaryAnnotationCount > 1) sizeInBytes += binaryAnnotationCount - 1; // commas
      for (int i = 0; i < binaryAnnotationCount; ) {
        V1BinaryAnnotation a = value.binaryAnnotations().get(i++);
        Endpoint endpoint = a.endpoint();
        int endpointSize;
        if (endpoint == null) {
          endpointSize = 0;
        } else if (endpoint.equals(lastEndpoint)) {
          endpointSize = lastEndpointSize;
        } else {
          lastEndpoint = endpoint;
          endpointSize = lastEndpointSize = endpointSizeInBytes(endpoint, true);
        }
        if (a.stringValue() != null) {
          sizeInBytes += binaryAnnotationSizeInBytes(a.key(), a.stringValue(), endpointSize);
        } else {
          sizeInBytes += 37; // {"key":"NN","value":true,"endpoint":}
          sizeInBytes += endpointSize;
        }
      }
    }

    if (Boolean.TRUE.equals(value.debug())) sizeInBytes += 13; // ,"debug":true
    return ++sizeInBytes; // }
  }

  @Override public void write(V1Span value, WriteBuffer b) {
    b.writeAscii("{\"traceId\":\"");
    if (value.traceIdHigh() != 0L) b.writeLongHex(value.traceIdHigh());
    b.writeLongHex(value.traceId());
    b.writeByte('"');
    if (value.parentId() != 0L) {
      b.writeAscii(",\"parentId\":\"");
      b.writeLongHex(value.parentId());
      b.writeByte('"');
    }
    b.writeAscii(",\"id\":\"");
    b.writeLongHex(value.id());
    b.writeByte('"');
    b.writeAscii(",\"name\":\"");
    if (value.name() != null) b.writeUtf8(jsonEscape(value.name()));
    b.writeByte('"');

    if (value.timestamp() != 0L) {
      b.writeAscii(",\"timestamp\":");
      b.writeAscii(value.timestamp());
    }
    if (value.duration() != 0L) {
      b.writeAscii(",\"duration\":");
      b.writeAscii(value.duration());
    }

    int annotationCount = value.annotations().size();
    Endpoint lastEndpoint = null;
    byte[] lastEndpointBytes = null;
    if (annotationCount > 0) {
      b.writeAscii(",\"annotations\":[");
      for (int i = 0; i < annotationCount; ) {
        V1Annotation a = value.annotations().get(i++);
        Endpoint endpoint = a.endpoint();
        byte[] endpointBytes;
        if (endpoint == null) {
          endpointBytes = null;
        } else if (endpoint.equals(lastEndpoint)) {
          endpointBytes = lastEndpointBytes;
        } else {
          lastEndpoint = endpoint;
          endpointBytes = lastEndpointBytes = legacyEndpointBytes(endpoint);
        }
        writeAnnotation(a.timestamp(), a.value(), endpointBytes, b);
        if (i < annotationCount) b.writeByte(',');
      }
      b.writeByte(']');
    }
    int binaryAnnotationCount = value.binaryAnnotations().size();
    if (binaryAnnotationCount > 0) {
      b.writeAscii(",\"binaryAnnotations\":[");
      for (int i = 0; i < binaryAnnotationCount; ) {
        V1BinaryAnnotation a = value.binaryAnnotations().get(i++);
        Endpoint endpoint = a.endpoint();
        byte[] endpointBytes;
        if (endpoint == null) {
          endpointBytes = null;
        } else if (endpoint.equals(lastEndpoint)) {
          endpointBytes = lastEndpointBytes;
        } else {
          lastEndpoint = endpoint;
          endpointBytes = lastEndpointBytes = legacyEndpointBytes(endpoint);
        }
        if (a.stringValue() != null) {
          writeBinaryAnnotation(a.key(), a.stringValue(), endpointBytes, b);
        } else {
          b.writeAscii("{\"key\":\"");
          b.writeAscii(a.key());
          b.writeAscii("\",\"value\":true,\"endpoint\":");
          b.write(endpointBytes);
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

  @Override public String toString() {
    return "Span";
  }

  static byte[] legacyEndpointBytes(@Nullable Endpoint localEndpoint) {
    if (localEndpoint == null) return null;
    byte[] result = new byte[endpointSizeInBytes(localEndpoint, true)];
    V2SpanWriter.writeEndpoint(localEndpoint, WriteBuffer.wrap(result), true);
    return result;
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

  static void writeBinaryAnnotation(String key, String value, @Nullable byte[] endpoint,
    WriteBuffer b) {
    b.writeAscii("{\"key\":\"");
    b.writeUtf8(jsonEscape(key));
    b.writeAscii("\",\"value\":\"");
    b.writeUtf8(jsonEscape(value));
    b.writeByte('"');
    if (endpoint != null) {
      b.writeAscii(",\"endpoint\":");
      b.write(endpoint);
    }
    b.writeAscii("}");
  }
}
