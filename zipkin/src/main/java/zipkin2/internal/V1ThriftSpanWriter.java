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

import java.util.List;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

import static zipkin2.internal.ThriftField.TYPE_BOOL;
import static zipkin2.internal.ThriftField.TYPE_I32;
import static zipkin2.internal.ThriftField.TYPE_I64;
import static zipkin2.internal.ThriftField.TYPE_LIST;
import static zipkin2.internal.ThriftField.TYPE_STOP;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.ThriftField.TYPE_STRUCT;
import static zipkin2.internal.WriteBuffer.utf8SizeInBytes;

/** This type isn't thread-safe: it re-uses state to avoid re-allocations in conversion loops. */
// @Immutable
public final class V1ThriftSpanWriter implements WriteBuffer.Writer<Span> {
  static final ThriftField TRACE_ID = new ThriftField(TYPE_I64, 1);
  static final ThriftField TRACE_ID_HIGH = new ThriftField(TYPE_I64, 12);
  static final ThriftField NAME = new ThriftField(TYPE_STRING, 3);
  static final ThriftField ID = new ThriftField(TYPE_I64, 4);
  static final ThriftField PARENT_ID = new ThriftField(TYPE_I64, 5);
  static final ThriftField ANNOTATIONS = new ThriftField(TYPE_LIST, 6);
  static final ThriftField BINARY_ANNOTATIONS = new ThriftField(TYPE_LIST, 8);
  static final ThriftField DEBUG = new ThriftField(TYPE_BOOL, 9);
  static final ThriftField TIMESTAMP = new ThriftField(TYPE_I64, 10);
  static final ThriftField DURATION = new ThriftField(TYPE_I64, 11);

  static final byte[] EMPTY_ARRAY = new byte[0];

  final V2SpanConverter converter = V2SpanConverter.create();

  @Override public int sizeInBytes(Span value) {
    V1Span v1Span = converter.convert(value);

    int endpointSize =
        value.localEndpoint() != null ? ThriftEndpointCodec.sizeInBytes(value.localEndpoint()) : 0;

    int sizeInBytes = 3 + 8; // TRACE_ID
    if (v1Span.traceIdHigh() != 0) sizeInBytes += 3 + 8; // TRACE_ID_HIGH
    if (v1Span.parentId() != 0) sizeInBytes += 3 + 8; // PARENT_ID
    sizeInBytes += 3 + 8; // ID
    sizeInBytes += 3 + 4; // NAME
    if (value.name() != null) sizeInBytes += utf8SizeInBytes(value.name());

    // we write list thriftFields even when empty to match finagle serialization
    sizeInBytes += 3 + 5; // ANNOTATION field + list overhead
    for (int i = 0, length = v1Span.annotations().size(); i < length; i++) {
      int valueSize = utf8SizeInBytes(v1Span.annotations().get(i).value());
      sizeInBytes += ThriftAnnotationWriter.sizeInBytes(valueSize, endpointSize);
    }

    sizeInBytes += 3 + 5; // BINARY_ANNOTATION field + list overhead
    for (int i = 0, length = v1Span.binaryAnnotations().size(); i < length; i++) {
      V1BinaryAnnotation b = v1Span.binaryAnnotations().get(i);
      int keySize = utf8SizeInBytes(b.key());
      if (b.stringValue() != null) {
        int valueSize = utf8SizeInBytes(b.stringValue());
        sizeInBytes += ThriftBinaryAnnotationWriter.sizeInBytes(keySize, valueSize, endpointSize);
      } else {
        int remoteEndpointSize = ThriftEndpointCodec.sizeInBytes(b.endpoint());
        sizeInBytes += ThriftBinaryAnnotationWriter.sizeInBytes(keySize, 1, remoteEndpointSize);
      }
    }

    if (v1Span.debug() != null) sizeInBytes += 3 + 1; // DEBUG
    if (v1Span.timestamp() != 0L) sizeInBytes += 3 + 8; // TIMESTAMP
    if (v1Span.duration() != 0L) sizeInBytes += 3 + 8; // DURATION

    sizeInBytes++; // TYPE_STOP
    return sizeInBytes;
  }

  @Override public void write(Span value, WriteBuffer buffer) {
    V1Span v1Span = converter.convert(value);
    byte[] endpointBytes = legacyEndpointBytes(value.localEndpoint());

    TRACE_ID.write(buffer);
    ThriftCodec.writeLong(buffer, v1Span.traceId());

    NAME.write(buffer);
    ThriftCodec.writeLengthPrefixed(buffer, value.name() != null ? value.name() : "");

    ID.write(buffer);
    ThriftCodec.writeLong(buffer, v1Span.id());

    if (v1Span.parentId() != 0L) {
      PARENT_ID.write(buffer);
      ThriftCodec.writeLong(buffer, v1Span.parentId());
    }

    // we write list thriftFields even when empty to match finagle serialization
    ANNOTATIONS.write(buffer);
    writeAnnotations(buffer, v1Span, endpointBytes);

    BINARY_ANNOTATIONS.write(buffer);
    writeBinaryAnnotations(buffer, v1Span, endpointBytes);

    if (v1Span.debug() != null) {
      DEBUG.write(buffer);
      buffer.writeByte(v1Span.debug() ? 1 : 0);
    }

    if (v1Span.timestamp() != 0L) {
      TIMESTAMP.write(buffer);
      ThriftCodec.writeLong(buffer, v1Span.timestamp());
    }
    if (v1Span.duration() != 0L) {
      DURATION.write(buffer);
      ThriftCodec.writeLong(buffer, v1Span.duration());
    }

    if (v1Span.traceIdHigh() != 0L) {
      TRACE_ID_HIGH.write(buffer);
      ThriftCodec.writeLong(buffer, v1Span.traceIdHigh());
    }

    buffer.writeByte(TYPE_STOP);
  }

  static void writeAnnotations(WriteBuffer buffer, V1Span v1Span, byte[] endpointBytes) {
    int annotationCount = v1Span.annotations().size();
    ThriftCodec.writeListBegin(buffer, annotationCount);
    for (int i = 0; i < annotationCount; i++) {
      V1Annotation a = v1Span.annotations().get(i);
      ThriftAnnotationWriter.write(a.timestamp(), a.value(), endpointBytes, buffer);
    }
  }

  static void writeBinaryAnnotations(WriteBuffer buffer, V1Span v1Span, byte[] endpointBytes) {
    int binaryAnnotationCount = v1Span.binaryAnnotations().size();
    ThriftCodec.writeListBegin(buffer, binaryAnnotationCount);
    for (int i = 0; i < binaryAnnotationCount; i++) {
      V1BinaryAnnotation a = v1Span.binaryAnnotations().get(i);
      byte[] ep = a.stringValue() != null ? endpointBytes : legacyEndpointBytes(a.endpoint());
      ThriftBinaryAnnotationWriter.write(a.key(), a.stringValue(), ep, buffer);
    }
  }

  @Override public String toString() {
    return "Span";
  }

  public byte[] writeList(List<Span> spans) {
    int lengthOfSpans = spans.size();
    if (lengthOfSpans == 0) return EMPTY_ARRAY;

    byte[] result = new byte[ThriftCodec.listSizeInBytes(this, spans)];
    ThriftCodec.writeList(this, spans, WriteBuffer.wrap(result));
    return result;
  }

  public byte[] write(Span onlySpan) {
    byte[] result = new byte[sizeInBytes(onlySpan)];
    write(onlySpan, WriteBuffer.wrap(result));
    return result;
  }

  public int writeList(List<Span> spans, byte[] out, int pos) {
    int lengthOfSpans = spans.size();
    if (lengthOfSpans == 0) return 0;

    WriteBuffer result = WriteBuffer.wrap(out, pos);
    ThriftCodec.writeList(this, spans, result);

    return result.pos() - pos;
  }

  static byte[] legacyEndpointBytes(@Nullable Endpoint localEndpoint) {
    if (localEndpoint == null) return null;
    byte[] result = new byte[ThriftEndpointCodec.sizeInBytes(localEndpoint)];
    ThriftEndpointCodec.write(localEndpoint, WriteBuffer.wrap(result));
    return result;
  }

  static class ThriftAnnotationWriter {

    static final ThriftField TIMESTAMP = new ThriftField(TYPE_I64, 1);
    static final ThriftField VALUE = new ThriftField(TYPE_STRING, 2);
    static final ThriftField ENDPOINT = new ThriftField(TYPE_STRUCT, 3);

    static int sizeInBytes(int valueSizeInBytes, int endpointSizeInBytes) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 8; // TIMESTAMP
      sizeInBytes += 3 + 4 + valueSizeInBytes;
      if (endpointSizeInBytes > 0) sizeInBytes += 3 + endpointSizeInBytes;
      sizeInBytes++; // TYPE_STOP
      return sizeInBytes;
    }

    static void write(long timestamp, String value, byte[] endpointBytes, WriteBuffer buffer) {
      TIMESTAMP.write(buffer);
      ThriftCodec.writeLong(buffer, timestamp);

      VALUE.write(buffer);
      ThriftCodec.writeLengthPrefixed(buffer, value);

      if (endpointBytes != null) {
        ENDPOINT.write(buffer);
        buffer.write(endpointBytes);
      }
      buffer.writeByte(TYPE_STOP);
    }
  }

  static class ThriftBinaryAnnotationWriter {

    static final ThriftField KEY = new ThriftField(TYPE_STRING, 1);
    static final ThriftField VALUE = new ThriftField(TYPE_STRING, 2);
    static final ThriftField TYPE = new ThriftField(TYPE_I32, 3);
    static final ThriftField ENDPOINT = new ThriftField(TYPE_STRUCT, 4);

    static int sizeInBytes(int keySize, int valueSize, int endpointSizeInBytes) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 4 + keySize;
      sizeInBytes += 3 + 4 + valueSize;
      sizeInBytes += 3 + 4; // TYPE
      if (endpointSizeInBytes > 0) sizeInBytes += 3 + endpointSizeInBytes;
      sizeInBytes++; // TYPE_STOP
      return sizeInBytes;
    }

    static void write(String key, String stringValue, byte[] endpointBytes, WriteBuffer buffer) {
      KEY.write(buffer);
      ThriftCodec.writeLengthPrefixed(buffer, key);

      VALUE.write(buffer);
      int type = 0;
      if (stringValue != null) {
        type = 6;
        ThriftCodec.writeInt(buffer, utf8SizeInBytes(stringValue));
        buffer.writeUtf8(stringValue);
      } else {
        ThriftCodec.writeInt(buffer, 1);
        buffer.writeByte(1);
      }

      TYPE.write(buffer);
      ThriftCodec.writeInt(buffer, type);

      if (endpointBytes != null) {
        ENDPOINT.write(buffer);
        buffer.write(endpointBytes);
      }

      buffer.writeByte(TYPE_STOP);
    }
  }
}
