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

import java.util.List;
import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Proto3Fields.BooleanField;
import zipkin2.internal.Proto3Fields.Field;
import zipkin2.internal.Proto3Fields.MapEntryField;
import zipkin2.internal.Proto3Fields.Utf8Field;

import static zipkin2.internal.Proto3Fields.BytesField;
import static zipkin2.internal.Proto3Fields.Fixed64Field;
import static zipkin2.internal.Proto3Fields.HexField;
import static zipkin2.internal.Proto3Fields.LengthDelimitedField;
import static zipkin2.internal.Proto3Fields.VarintField;

//@Immutable
final class Proto3ZipkinFields {
  /** This is the only field in the ListOfSpans type */
  static final SpanField SPAN = new SpanField();
  static final Endpoint EMPTY_ENDPOINT = Endpoint.newBuilder().build();

  static class EndpointField extends LengthDelimitedField<Endpoint> {
    static final int SERVICE_NAME_FIELD = 1;
    static final int IPV4_FIELD = 2;
    static final int IPV6_FIELD = 3;
    static final int PORT_FIELD = 4;

    static final Utf8Field SERVICE_NAME = new Utf8Field(SERVICE_NAME_FIELD);
    static final BytesField IPV4 = new BytesField(IPV4_FIELD);
    static final BytesField IPV6 = new BytesField(IPV6_FIELD);
    static final VarintField PORT = new VarintField(PORT_FIELD);

    EndpointField(int fieldNumber) {
      super(fieldNumber);
    }

    @Override int sizeOfValue(Endpoint value) {
      if (EMPTY_ENDPOINT.equals(value)) return 0;
      int result = 0;
      result += SERVICE_NAME.sizeInBytes(value.serviceName());
      result += IPV4.sizeInBytes(value.ipv4Bytes());
      result += IPV6.sizeInBytes(value.ipv6Bytes());
      result += PORT.sizeInBytes(value.portAsInt());
      return result;
    }

    @Override void writeValue(Buffer b, Endpoint value) {
      SERVICE_NAME.write(b, value.serviceName());
      IPV4.write(b, value.ipv4Bytes());
      IPV6.write(b, value.ipv6Bytes());
      PORT.write(b, value.portAsInt());
    }
  }

  static class AnnotationField extends LengthDelimitedField<Annotation> {
    static final int TIMESTAMP_FIELD = 1;
    static final int VALUE_FIELD = 2;

    static final Fixed64Field TIMESTAMP = new Fixed64Field(TIMESTAMP_FIELD);
    static final Utf8Field VALUE = new Utf8Field(VALUE_FIELD);

    AnnotationField(int fieldNumber) {
      super(fieldNumber);
    }

    @Override int sizeOfValue(Annotation value) {
      return TIMESTAMP.sizeInBytes(value.timestamp()) + VALUE.sizeInBytes(value.value());
    }

    @Override void writeValue(Buffer b, Annotation value) {
      TIMESTAMP.write(b, value.timestamp());
      VALUE.write(b, value.value());
    }
  }

  static class SpanField extends LengthDelimitedField<Span> {
    static final int TRACE_ID_FIELD = 1;
    static final int PARENT_ID_FIELD = 2;
    static final int ID_FIELD = 3;
    static final int KIND_FIELD = 4;
    static final int NAME_FIELD = 5;
    static final int TIMESTAMP_FIELD = 6;
    static final int DURATION_FIELD = 7;
    static final int LOCAL_ENDPOINT_FIELD = 8;
    static final int REMOTE_ENDPOINT_FIELD = 9;
    static final int ANNOTATION_FIELD = 10;
    static final int TAG_FIELD = 11;
    static final int DEBUG_FIELD = 12;
    static final int SHARED_FIELD = 13;

    static final HexField TRACE_ID = new HexField(TRACE_ID_FIELD);
    static final HexField PARENT_ID = new HexField(PARENT_ID_FIELD);
    static final HexField ID = new HexField(ID_FIELD);
    static final VarintField KIND = new VarintField(KIND_FIELD);
    static final Utf8Field NAME = new Utf8Field(NAME_FIELD);
    static final Fixed64Field TIMESTAMP = new Fixed64Field(TIMESTAMP_FIELD);
    static final VarintField DURATION = new VarintField(DURATION_FIELD);
    static final EndpointField LOCAL_ENDPOINT = new EndpointField(LOCAL_ENDPOINT_FIELD);
    static final EndpointField REMOTE_ENDPOINT = new EndpointField(REMOTE_ENDPOINT_FIELD);
    static final AnnotationField ANNOTATION = new AnnotationField(ANNOTATION_FIELD);
    static final MapEntryField TAG = new MapEntryField(TAG_FIELD);
    static final BooleanField DEBUG = new BooleanField(DEBUG_FIELD);
    static final BooleanField SHARED = new BooleanField(SHARED_FIELD);

    SpanField() {
      super(1);
    }

    @Override int sizeOfValue(Span span) {
      int sizeOfSpan = TRACE_ID.sizeInBytes(span.traceId());
      sizeOfSpan += PARENT_ID.sizeInBytes(span.parentId());
      sizeOfSpan += ID.sizeInBytes(span.id());
      sizeOfSpan += KIND.sizeInBytes(span.kind() != null ? 1 : 0);
      sizeOfSpan += NAME.sizeInBytes(span.name());
      sizeOfSpan += TIMESTAMP.sizeInBytes(span.timestampAsLong());
      sizeOfSpan += DURATION.sizeInBytes(span.durationAsLong());
      sizeOfSpan += LOCAL_ENDPOINT.sizeInBytes(span.localEndpoint());
      sizeOfSpan += REMOTE_ENDPOINT.sizeInBytes(span.remoteEndpoint());

      List<Annotation> annotations = span.annotations();
      int annotationCount = annotations.size();
      for (int i = 0; i < annotationCount; i++) {
        sizeOfSpan += ANNOTATION.sizeInBytes(annotations.get(i));
      }

      Map<String, String> tags = span.tags();
      int tagCount = tags.size();
      if (tagCount > 0) { // avoid allocating an iterator when empty
        for (Map.Entry<String, String> entry : tags.entrySet()) {
          sizeOfSpan += TAG.sizeInBytes(entry);
        }
      }

      sizeOfSpan += DEBUG.sizeInBytes(Boolean.TRUE.equals(span.debug()));
      sizeOfSpan += SHARED.sizeInBytes(Boolean.TRUE.equals(span.shared()));
      return sizeOfSpan;
    }

    @Override void writeValue(Buffer b, Span value) {
      TRACE_ID.write(b, value.traceId());
      PARENT_ID.write(b, value.parentId());
      ID.write(b, value.id());
      KIND.write(b, toByte(value.kind()));
      NAME.write(b, value.name());
      TIMESTAMP.write(b, value.timestampAsLong());
      DURATION.write(b, value.durationAsLong());
      LOCAL_ENDPOINT.write(b, value.localEndpoint());
      REMOTE_ENDPOINT.write(b, value.remoteEndpoint());

      List<Annotation> annotations = value.annotations();
      int annotationLength = annotations.size();
      for (int i = 0; i < annotationLength; i++) {
        ANNOTATION.write(b, annotations.get(i));
      }

      Map<String, String> tags = value.tags();
      if (!tags.isEmpty()) { // avoid allocating an iterator when empty
        for (Map.Entry<String, String> entry : tags.entrySet()) {
          TAG.write(b, entry);
        }
      }

      SpanField.DEBUG.write(b, Boolean.TRUE.equals(value.debug()));
      SpanField.SHARED.write(b, Boolean.TRUE.equals(value.shared()));
    }

    // in java, there's no zero index for unknown
    int toByte(Span.Kind kind) {
      return kind != null ? kind.ordinal() + 1 : 0;
    }

    public Span read(Buffer buffer) {
      readThisKey(buffer);
      return readValue(buffer);
    }

    Span readValue(Buffer buffer) {
      int length = ensureLength(buffer);
      if (length == 0) return null;
      int endPos = buffer.pos + length;

      // now, we are in the span fields
      Span.Builder builder = Span.newBuilder();
      while (buffer.pos < endPos) {
        int nextKey = buffer.readVarint32();
        int lastPos = buffer.pos - 1;
        int nextWireType = wireType(nextKey, lastPos);
        int nextFieldNumber = fieldNumber(nextKey, lastPos);
        switch (nextFieldNumber) {
          case TRACE_ID_FIELD:
            checkWireType(lastPos, nextWireType, "Span.traceId", TRACE_ID);
            builder.traceId(TRACE_ID.readValue(buffer));
            break;
          case PARENT_ID_FIELD:
            checkWireType(lastPos, nextWireType, "Span.parentId", PARENT_ID);
            builder.parentId(PARENT_ID.readValue(buffer));
            break;
          case ID_FIELD:
            checkWireType(lastPos, nextWireType, "Span.id", ID);
            builder.id(ID.readValue(buffer));
            break;
          case KIND_FIELD:
            checkWireType(lastPos, nextWireType, "Span.kind", KIND);
            int kind = buffer.readVarint32();
            builder.kind(Span.Kind.values()[kind]);
            break;
          case NAME_FIELD:
            checkWireType(lastPos, nextWireType, "Span.name", NAME);
            builder.name(NAME.readValue(buffer));
            break;
          case TIMESTAMP_FIELD:
            checkWireType(lastPos, nextWireType, "Span.timestamp", TIMESTAMP);
            builder.timestamp(TIMESTAMP.readValue(buffer));
            break;
          case DURATION_FIELD:
            checkWireType(lastPos, nextWireType, "Span.duration", DURATION);
            builder.duration(buffer.readVarint64());
            break;
          case LOCAL_ENDPOINT_FIELD:
            checkWireType(lastPos, nextWireType, "Span.localEndpoint", LOCAL_ENDPOINT);
            break;
          case REMOTE_ENDPOINT_FIELD:
            checkWireType(lastPos, nextWireType, "Span.remoteEndpoint", REMOTE_ENDPOINT);
            break;
          case ANNOTATION_FIELD:
            checkWireType(lastPos, nextWireType, "Span.annotations", ANNOTATION);
            break;
          case TAG_FIELD:
            checkWireType(lastPos, nextWireType, "Span.tags", TAG);
            break;
          case DEBUG_FIELD:
            checkWireType(lastPos, nextWireType, "Span.debug", DEBUG);
            if (DEBUG.read(buffer)) builder.debug(true);
            break;
          case SHARED_FIELD:
            checkWireType(lastPos, nextWireType, "Span.shared", SHARED);
            if (SHARED.read(buffer)) builder.shared(true);
            break;
          default:
            skipValue(buffer, nextWireType);
        }
      }
      return builder.build();
    }
  }

  static void checkWireType(int pos, int wireType, String fieldName, Field field) {
    if (wireType == field.wireType) return;
    throw new IllegalArgumentException(
      "wireType " + wireType + " at position " + pos + " was not " + fieldName + "'s wireType "
        + field.wireType
    );
  }
}
