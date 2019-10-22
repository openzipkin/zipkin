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
import java.util.Map;
import java.util.logging.Logger;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Proto3Fields.BooleanField;
import zipkin2.internal.Proto3Fields.Utf8Field;

import static java.util.logging.Level.FINE;
import static zipkin2.internal.Proto3Fields.BytesField;
import static zipkin2.internal.Proto3Fields.Field.fieldNumber;
import static zipkin2.internal.Proto3Fields.Field.skipValue;
import static zipkin2.internal.Proto3Fields.Field.wireType;
import static zipkin2.internal.Proto3Fields.Fixed64Field;
import static zipkin2.internal.Proto3Fields.HexField;
import static zipkin2.internal.Proto3Fields.LengthDelimitedField;
import static zipkin2.internal.Proto3Fields.VarintField;
import static zipkin2.internal.Proto3Fields.WIRETYPE_FIXED64;
import static zipkin2.internal.Proto3Fields.WIRETYPE_LENGTH_DELIMITED;
import static zipkin2.internal.Proto3Fields.WIRETYPE_VARINT;

/** Keys are used in this class because while verbose, it allows us to use switch statements */
//@Immutable
final class Proto3ZipkinFields {
  static final Logger LOG = Logger.getLogger(Proto3ZipkinFields.class.getName());
  /** This is the only field in the ListOfSpans type */
  static final SpanField SPAN = new SpanField();

  static class EndpointField extends LengthDelimitedField<Endpoint> {
    static final int SERVICE_NAME_KEY = (1 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int IPV4_KEY = (2 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int IPV6_KEY = (3 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int PORT_KEY = (4 << 3) | WIRETYPE_VARINT;

    static final Utf8Field SERVICE_NAME = new Utf8Field(SERVICE_NAME_KEY);
    static final BytesField IPV4 = new BytesField(IPV4_KEY);
    static final BytesField IPV6 = new BytesField(IPV6_KEY);
    static final VarintField PORT = new VarintField(PORT_KEY);

    EndpointField(int key) {
      super(key);
    }

    @Override int sizeOfValue(Endpoint value) {
      int result = 0;
      result += SERVICE_NAME.sizeInBytes(value.serviceName());
      result += IPV4.sizeInBytes(value.ipv4Bytes());
      result += IPV6.sizeInBytes(value.ipv6Bytes());
      result += PORT.sizeInBytes(value.portAsInt());
      return result;
    }

    @Override void writeValue(WriteBuffer b, Endpoint value) {
      SERVICE_NAME.write(b, value.serviceName());
      IPV4.write(b, value.ipv4Bytes());
      IPV6.write(b, value.ipv6Bytes());
      PORT.write(b, value.portAsInt());
    }

    @Override Endpoint readValue(ReadBuffer buffer, int length) {
      int endPos = buffer.pos() + length;

      // now, we are in the endpoint fields
      Endpoint.Builder builder = Endpoint.newBuilder();
      while (buffer.pos() < endPos) {
        int nextKey = buffer.readVarint32();
        switch (nextKey) {
          case SERVICE_NAME_KEY:
            builder.serviceName(SERVICE_NAME.readLengthPrefixAndValue(buffer));
            break;
          case IPV4_KEY:
            builder.parseIp(IPV4.readLengthPrefixAndValue(buffer));
            break;
          case IPV6_KEY:
            builder.parseIp(IPV6.readLengthPrefixAndValue(buffer));
            break;
          case PORT_KEY:
            builder.port(buffer.readVarint32());
            break;
          default:
            logAndSkip(buffer, nextKey);
        }
      }
      return builder.build();
    }
  }

  /** Contributes to a builder as opposed to reading values directly. Avoids allocation. */
  static abstract class SpanBuilderField<T> extends LengthDelimitedField<T> {

    SpanBuilderField(int key) {
      super(key);
    }

    @Override final T readValue(ReadBuffer b, int length) {
      throw new UnsupportedOperationException();
    }

    abstract boolean readLengthPrefixAndValue(ReadBuffer b, Span.Builder builder);
  }

  static class AnnotationField extends SpanBuilderField<Annotation> {
    static final int TIMESTAMP_KEY = (1 << 3) | WIRETYPE_FIXED64;
    static final int VALUE_KEY = (2 << 3) | WIRETYPE_LENGTH_DELIMITED;

    static final Fixed64Field TIMESTAMP = new Fixed64Field(TIMESTAMP_KEY);
    static final Utf8Field VALUE = new Utf8Field(VALUE_KEY);

    AnnotationField(int key) {
      super(key);
    }

    @Override int sizeOfValue(Annotation value) {
      return TIMESTAMP.sizeInBytes(value.timestamp()) + VALUE.sizeInBytes(value.value());
    }

    @Override void writeValue(WriteBuffer b, Annotation value) {
      TIMESTAMP.write(b, value.timestamp());
      VALUE.write(b, value.value());
    }

    @Override boolean readLengthPrefixAndValue(ReadBuffer b, Span.Builder builder) {
      int length = b.readVarint32();
      if (length == 0) return false;
      int endPos = b.pos() + length;

      // now, we are in the annotation fields
      long timestamp = 0L;
      String value = null;
      while (b.pos() < endPos) {
        int nextKey = b.readVarint32();
        switch (nextKey) {
          case TIMESTAMP_KEY:
            timestamp = TIMESTAMP.readValue(b);
            break;
          case VALUE_KEY:
            value = VALUE.readLengthPrefixAndValue(b);
            break;
          default:
            logAndSkip(b, nextKey);
        }
      }
      if (timestamp == 0L || value == null) return false;
      builder.addAnnotation(timestamp, value);
      return true;
    }
  }

  static final class TagField extends SpanBuilderField<Map.Entry<String, String>> {
    // map<string,string> in proto is a special field with key, value
    static final int KEY_KEY = (1 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int VALUE_KEY = (2 << 3) | WIRETYPE_LENGTH_DELIMITED;

    static final Utf8Field KEY = new Utf8Field(KEY_KEY);
    static final Utf8Field VALUE = new Utf8Field(VALUE_KEY);

    TagField(int key) {
      super(key);
    }

    @Override int sizeOfValue(Map.Entry<String, String> value) {
      return KEY.sizeInBytes(value.getKey()) + VALUE.sizeInBytes(value.getValue());
    }

    @Override void writeValue(WriteBuffer b, Map.Entry<String, String> value) {
      KEY.write(b, value.getKey());
      VALUE.write(b, value.getValue());
    }

    @Override boolean readLengthPrefixAndValue(ReadBuffer b, Span.Builder builder) {
      int length = b.readVarint32();
      if (length == 0) return false;
      int endPos = b.pos() + length;

      // now, we are in the tag fields
      String key = null, value = ""; // empty tags allowed
      while (b.pos() < endPos) {
        int nextKey = b.readVarint32();
        switch (nextKey) {
          case KEY_KEY:
            key = KEY.readLengthPrefixAndValue(b);
            break;
          case VALUE_KEY:
            String read = VALUE.readLengthPrefixAndValue(b);
            if (read != null) value = read; // empty tags allowed
            break;
          default:
            logAndSkip(b, nextKey);
        }
      }
      if (key == null) return false;
      builder.putTag(key, value);
      return true;
    }
  }

  static class SpanField extends LengthDelimitedField<Span> {
    static final int TRACE_ID_KEY = (1 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int PARENT_ID_KEY = (2 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int ID_KEY = (3 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int KIND_KEY = (4 << 3) | WIRETYPE_VARINT;
    static final int NAME_KEY = (5 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int TIMESTAMP_KEY = (6 << 3) | WIRETYPE_FIXED64;
    static final int DURATION_KEY = (7 << 3) | WIRETYPE_VARINT;
    static final int LOCAL_ENDPOINT_KEY = (8 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int REMOTE_ENDPOINT_KEY = (9 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int ANNOTATION_KEY = (10 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int TAG_KEY = (11 << 3) | WIRETYPE_LENGTH_DELIMITED;
    static final int DEBUG_KEY = (12 << 3) | WIRETYPE_VARINT;
    static final int SHARED_KEY = (13 << 3) | WIRETYPE_VARINT;

    static final HexField TRACE_ID = new HexField(TRACE_ID_KEY);
    static final HexField PARENT_ID = new HexField(PARENT_ID_KEY);
    static final HexField ID = new HexField(ID_KEY);
    static final VarintField KIND = new VarintField(KIND_KEY);
    static final Utf8Field NAME = new Utf8Field(NAME_KEY);
    static final Fixed64Field TIMESTAMP = new Fixed64Field(TIMESTAMP_KEY);
    static final VarintField DURATION = new VarintField(DURATION_KEY);
    static final EndpointField LOCAL_ENDPOINT = new EndpointField(LOCAL_ENDPOINT_KEY);
    static final EndpointField REMOTE_ENDPOINT = new EndpointField(REMOTE_ENDPOINT_KEY);
    static final AnnotationField ANNOTATION = new AnnotationField(ANNOTATION_KEY);
    static final TagField TAG = new TagField(TAG_KEY);
    static final BooleanField DEBUG = new BooleanField(DEBUG_KEY);
    static final BooleanField SHARED = new BooleanField(SHARED_KEY);

    SpanField() {
      super((1 << 3) | WIRETYPE_LENGTH_DELIMITED);
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

    @Override void writeValue(WriteBuffer b, Span value) {
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

    public Span read(ReadBuffer buffer) {
      buffer.readVarint32(); // toss the key
      return readLengthPrefixAndValue(buffer);
    }

    @Override Span readValue(ReadBuffer buffer, int length) {
      buffer.require(length); // more convenient to check up-front vs partially read
      int endPos = buffer.pos() + length;

      // now, we are in the span fields
      Span.Builder builder = Span.newBuilder();
      while (buffer.pos() < endPos) {
        int nextKey = buffer.readVarint32();
        switch (nextKey) {
          case TRACE_ID_KEY:
            builder.traceId(TRACE_ID.readLengthPrefixAndValue(buffer));
            break;
          case PARENT_ID_KEY:
            builder.parentId(PARENT_ID.readLengthPrefixAndValue(buffer));
            break;
          case ID_KEY:
            builder.id(ID.readLengthPrefixAndValue(buffer));
            break;
          case KIND_KEY:
            int kind = buffer.readVarint32();
            if (kind == 0) break;
            if (kind > Span.Kind.values().length) break;
            builder.kind(Span.Kind.values()[kind - 1]);
            break;
          case NAME_KEY:
            builder.name(NAME.readLengthPrefixAndValue(buffer));
            break;
          case TIMESTAMP_KEY:
            builder.timestamp(TIMESTAMP.readValue(buffer));
            break;
          case DURATION_KEY:
            builder.duration(buffer.readVarint64());
            break;
          case LOCAL_ENDPOINT_KEY:
            builder.localEndpoint(LOCAL_ENDPOINT.readLengthPrefixAndValue(buffer));
            break;
          case REMOTE_ENDPOINT_KEY:
            builder.remoteEndpoint(REMOTE_ENDPOINT.readLengthPrefixAndValue(buffer));
            break;
          case ANNOTATION_KEY:
            ANNOTATION.readLengthPrefixAndValue(buffer, builder);
            break;
          case TAG_KEY:
            TAG.readLengthPrefixAndValue(buffer, builder);
            break;
          case DEBUG_KEY:
            if (DEBUG.read(buffer)) builder.debug(true);
            break;
          case SHARED_KEY:
            if (SHARED.read(buffer)) builder.shared(true);
            break;
          default:
            logAndSkip(buffer, nextKey);
        }
      }
      return builder.build();
    }
  }

  static void logAndSkip(ReadBuffer buffer, int nextKey) {
    int nextWireType = wireType(nextKey, buffer.pos());
    if (LOG.isLoggable(FINE)) {
      int nextFieldNumber = fieldNumber(nextKey, buffer.pos());
      LOG.fine(String.format("Skipping field: byte=%s, fieldNumber=%s, wireType=%s",
        buffer.pos(), nextFieldNumber, nextWireType));
    }
    skipValue(buffer, nextWireType);
  }
}
