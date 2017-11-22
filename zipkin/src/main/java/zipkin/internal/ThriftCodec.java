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
package zipkin.internal;

import java.io.EOFException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.assertionError;
import static zipkin.internal.Util.checkArgument;

/**
 * This is a hard-coded thrift codec, which allows us to include thrift marshalling in a minified
 * core jar. The hard coding not only keeps us with a single data-model, it also allows the minified
 * core jar free of SLFJ classes otherwise included in generated types.
 *
 * <p> This directly implements TBinaryProtocol so as to reduce dependencies and array duplication.
 * While reads internally use {@link ByteBuffer}, writes use {@link Buffer} as the latter can grow.
 */
public final class ThriftCodec implements Codec {
  // break vs decode huge structs, like > 1MB strings or 10k spans in a trace.
  static final int STRING_LENGTH_LIMIT = 1 * 1024 * 1024;
  static final int CONTAINER_LENGTH_LIMIT = 10 * 1000;
  // break vs recursing infinitely when skipping data
  static final int MAX_SKIP_DEPTH = 2147483647;

  // taken from org.apache.thrift.protocol.TType
  static final byte TYPE_STOP = 0;
  static final byte TYPE_BOOL = 2;
  static final byte TYPE_BYTE = 3;
  static final byte TYPE_DOUBLE = 4;
  static final byte TYPE_I16 = 6;
  static final byte TYPE_I32 = 8;
  static final byte TYPE_I64 = 10;
  static final byte TYPE_STRING = 11;
  static final byte TYPE_STRUCT = 12;
  static final byte TYPE_MAP = 13;
  static final byte TYPE_SET = 14;
  static final byte TYPE_LIST = 15;

  /**
   * Added for DataStax Cassandra driver, which returns data in ByteBuffers. The implementation
   * takes care not to re-buffer the data.
   *
   * @throws {@linkplain IllegalArgumentException} if the span couldn't be decoded
   */
  public Span readSpan(ByteBuffer bytes) {
    return read(new SpanReader(), bytes);
  }

  @Override
  public Span readSpan(byte[] bytes) {
    return read(new SpanReader(), ByteBuffer.wrap(bytes));
  }

  @Override public int sizeInBytes(Span value) {
    return SPAN_WRITER.sizeInBytes(value);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SPAN_WRITER, value);
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
    return readList(new SpanReader(), ByteBuffer.wrap(bytes));
  }

  @Override
  public byte[] writeSpans(List<Span> value) {
    return writeList(SPAN_WRITER, value);
  }

  @Override
  public byte[] writeTraces(List<List<Span>> values) {
    int traceCount = values.size();
    // Get the encoded size of the nested list so that we don't need to grow the buffer
    int sizeInBytes = 5;
    for (int i = 0; i < traceCount; i++) {
      sizeInBytes += listSizeInBytes(SPAN_WRITER, values.get(i));
    }

    Buffer out = new Buffer(sizeInBytes);
    writeListBegin(out, traceCount);
    try {
      for (int i = 0; i < traceCount; i++) {
        writeList(SPAN_WRITER, values.get(i));
      }
    } catch (RuntimeException e) {
      throw assertionError("Could not write " + values + " as TBinary", e);
    }
    return out.toByteArray();
  }

  interface ThriftReader<T> {
    T read(ByteBuffer bytes);
  }

  interface ThriftAdapter<T> extends ThriftReader<T>, Buffer.Writer<T> {
  }

  static final ThriftAdapter<Endpoint> ENDPOINT_ADAPTER = new ThriftAdapter<Endpoint>() {

    final Field IPV4 = new Field(TYPE_I32, 1);
    final Field PORT = new Field(TYPE_I16, 2);
    final Field SERVICE_NAME = new Field(TYPE_STRING, 3);
    final Field IPV6 = new Field(TYPE_STRING, 4);

    @Override
    public Endpoint read(ByteBuffer bytes) {
      Endpoint.Builder result = Endpoint.builder();
      Field field;

      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(IPV4)) {
          result.ipv4(bytes.getInt());
        } else if (field.isEqualTo(PORT)) {
          result.port(Short.valueOf(bytes.getShort()));
        } else if (field.isEqualTo(SERVICE_NAME)) {
          result.serviceName(readUtf8(bytes));
        } else if (field.isEqualTo(IPV6)) {
          result.ipv6(readByteArray(bytes));
        } else {
          skip(bytes, field.type);
        }
      }
      return result.build();
    }

    @Override public int sizeInBytes(Endpoint value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 4;// IPV4
      sizeInBytes += 3 + 2;// PORT
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.serviceName);
      if (value.ipv6 != null) sizeInBytes += 3 + 4 + 16;
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override
    public void write(Endpoint value, Buffer buffer) {
      IPV4.write(buffer);
      buffer.writeInt(value.ipv4);

      PORT.write(buffer);
      buffer.writeShort(value.port == null ? 0 : value.port);

      SERVICE_NAME.write(buffer);
      buffer.writeLengthPrefixed(checkStringLength(value.serviceName, STRING_LENGTH_LIMIT));

      if (value.ipv6 != null) {
        IPV6.write(buffer);
        assert value.ipv6.length == 16;
        buffer.writeInt(value.ipv6.length);
        buffer.write(value.ipv6);
      }

      buffer.writeByte(TYPE_STOP);
    }
  };

  static final ThriftAdapter<Annotation> ANNOTATION_ADAPTER = new ThriftAdapter<Annotation>() {

    final Field TIMESTAMP = new Field(TYPE_I64, 1);
    final Field VALUE = new Field(TYPE_STRING, 2);
    final Field ENDPOINT = new Field(TYPE_STRUCT, 3);

    @Override
    public Annotation read(ByteBuffer bytes) {
      Annotation.Builder result = Annotation.builder();
      Field field;
      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(TIMESTAMP)) {
          result.timestamp(bytes.getLong());
        } else if (field.isEqualTo(VALUE)) {
          result.value(readUtf8(bytes));
        } else if (field.isEqualTo(ENDPOINT)) {
          result.endpoint(ENDPOINT_ADAPTER.read(bytes));
        } else {
          skip(bytes, field.type);
        }
      }
      return result.build();
    }

    @Override public int sizeInBytes(Annotation value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 8;// TIMESTAMP
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.value);
      if (value.endpoint != null) sizeInBytes += 3 + ENDPOINT_ADAPTER.sizeInBytes(value.endpoint);
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override
    public void write(Annotation value, Buffer buffer) {
      TIMESTAMP.write(buffer);
      buffer.writeLong(value.timestamp);

      VALUE.write(buffer);
      buffer.writeLengthPrefixed(checkStringLength(value.value, STRING_LENGTH_LIMIT));

      if (value.endpoint != null) {
        ENDPOINT.write(buffer);
        ENDPOINT_ADAPTER.write(value.endpoint, buffer);
      }
      buffer.writeByte(TYPE_STOP);
    }
  };

  static final ThriftAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new ThriftAdapter<BinaryAnnotation>() {

    final Field KEY = new Field(TYPE_STRING, 1);
    final Field VALUE = new Field(TYPE_STRING, 2);
    final Field TYPE = new Field(TYPE_I32, 3);
    final Field ENDPOINT = new Field(TYPE_STRUCT, 4);

    @Override public BinaryAnnotation read(ByteBuffer bytes) {
      BinaryAnnotation.Builder result = BinaryAnnotation.builder();
      Field field;

      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(KEY)) {
          result.key(readUtf8(bytes));
        } else if (field.isEqualTo(VALUE)) {
          result.value(readByteArray(bytes));
        } else if (field.isEqualTo(TYPE)) {
          result.type(BinaryAnnotation.Type.fromValue(bytes.getInt()));
        } else if (field.isEqualTo(ENDPOINT)) {
          result.endpoint(ENDPOINT_ADAPTER.read(bytes));
        } else {
          skip(bytes, field.type);
        }
      }
      return result.build();
    }

    @Override public int sizeInBytes(BinaryAnnotation value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.key);
      sizeInBytes += 3 + 4 + value.value.length;
      sizeInBytes += 3 + 4; // TYPE
      if (value.endpoint != null)  sizeInBytes += 3 + ENDPOINT_ADAPTER.sizeInBytes(value.endpoint);
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override
    public void write(BinaryAnnotation value, Buffer buffer) {
      KEY.write(buffer);
      buffer.writeLengthPrefixed(checkStringLength(value.key, STRING_LENGTH_LIMIT));

      VALUE.write(buffer);
      buffer.writeInt(value.value.length);
      buffer.write(checkByteArrayLength(value.value, STRING_LENGTH_LIMIT));

      TYPE.write(buffer);
      buffer.writeInt(value.type.value);

      if (value.endpoint != null) {
        ENDPOINT.write(buffer);
        ENDPOINT_ADAPTER.write(value.endpoint, buffer);
      }

      buffer.writeByte(TYPE_STOP);
    }
  };

  static final class SpanReader implements ThriftReader<Span> {

    static final Field TRACE_ID = new Field(TYPE_I64, 1);
    static final Field TRACE_ID_HIGH = new Field(TYPE_I64, 12);
    static final Field NAME = new Field(TYPE_STRING, 3);
    static final Field ID = new Field(TYPE_I64, 4);
    static final Field PARENT_ID = new Field(TYPE_I64, 5);
    static final Field ANNOTATIONS = new Field(TYPE_LIST, 6);
    static final Field BINARY_ANNOTATIONS = new Field(TYPE_LIST, 8);
    static final Field DEBUG = new Field(TYPE_BOOL, 9);
    static final Field TIMESTAMP = new Field(TYPE_I64, 10);
    static final Field DURATION = new Field(TYPE_I64, 11);

    Span.Builder builder = Span.builder();

    @Override public Span read(ByteBuffer bytes) {
      if (builder == null) {
        builder = Span.builder();
      } else {
        builder.clear();
      }

      Field field;

      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(TRACE_ID_HIGH)) {
          builder.traceIdHigh(bytes.getLong());
        } else if (field.isEqualTo(TRACE_ID)) {
          builder.traceId(bytes.getLong());
        } else if (field.isEqualTo(NAME)) {
          builder.name(readUtf8(bytes));
        } else if (field.isEqualTo(ID)) {
          builder.id(bytes.getLong());
        } else if (field.isEqualTo(PARENT_ID)) {
          builder.parentId(bytes.getLong());
        } else if (field.isEqualTo(ANNOTATIONS)) {
          byte ignoredType = bytes.get();
          int length = guardLength(bytes, CONTAINER_LENGTH_LIMIT);
          for (int i = 0; i < length; i++) {
            builder.addAnnotation(ANNOTATION_ADAPTER.read(bytes));
          }
        } else if (field.isEqualTo(BINARY_ANNOTATIONS)) {
          byte ignoredType = bytes.get();
          int length = guardLength(bytes, CONTAINER_LENGTH_LIMIT);
          for (int i = 0; i < length; i++) {
            builder.addBinaryAnnotation(BINARY_ANNOTATION_ADAPTER.read(bytes));
          }
        } else if (field.isEqualTo(DEBUG)) {
          builder.debug(bytes.get() == 1);
        } else if (field.isEqualTo(TIMESTAMP)) {
          builder.timestamp(bytes.getLong());
        } else if (field.isEqualTo(DURATION)) {
          builder.duration(bytes.getLong());
        } else {
          skip(bytes, field.type);
        }
      }

      return builder.build();
    }

    @Override public String toString() {
      return "Span";
    }
  }

  static final Buffer.Writer<Span> SPAN_WRITER = new Buffer.Writer<Span>() {
    @Override public int sizeInBytes(Span value) {
      int sizeInBytes = 0;
      if (value.traceIdHigh != 0) sizeInBytes += 3 + 8;
      sizeInBytes += 3 + 8;// TRACE_ID
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.name);
      sizeInBytes += 3 + 8;// ID
      if (value.parentId != null) sizeInBytes += 3 + 8;
      sizeInBytes += 3 + listSizeInBytes(ANNOTATION_ADAPTER, value.annotations);
      sizeInBytes += 3 + listSizeInBytes(BINARY_ANNOTATION_ADAPTER, value.binaryAnnotations);
      if (value.debug != null && value.debug) sizeInBytes += 3 + 1;
      if (value.timestamp != null) sizeInBytes += 3 + 8;
      if (value.duration != null) sizeInBytes += 3 + 8;
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override public void write(Span value, Buffer buffer) {
      SpanReader.TRACE_ID.write(buffer);
      buffer.writeLong(value.traceId);

      SpanReader.NAME.write(buffer);
      buffer.writeLengthPrefixed(checkStringLength(value.name, STRING_LENGTH_LIMIT));

      SpanReader.ID.write(buffer);
      buffer.writeLong(value.id);

      if (value.parentId != null) {
        SpanReader.PARENT_ID.write(buffer);
        buffer.writeLong(value.parentId);
      }

      // we write list fields even when empty to match finagle serialization
      SpanReader.ANNOTATIONS.write(buffer);
      writeList(ANNOTATION_ADAPTER, value.annotations, buffer);

      SpanReader.BINARY_ANNOTATIONS.write(buffer);
      writeList(BINARY_ANNOTATION_ADAPTER, value.binaryAnnotations, buffer);

      if (value.debug != null && value.debug) {
        SpanReader.DEBUG.write(buffer);
        buffer.writeByte(1);
      }

      if (value.timestamp != null) {
        SpanReader.TIMESTAMP.write(buffer);
        buffer.writeLong(value.timestamp);
      }

      if (value.duration != null) {
        SpanReader.DURATION.write(buffer);
        buffer.writeLong(value.duration);
      }

      if (value.traceIdHigh != 0) {
        SpanReader.TRACE_ID_HIGH.write(buffer);
        buffer.writeLong(value.traceIdHigh);
      }

      buffer.writeByte(TYPE_STOP);
    }

    @Override
    public String toString() {
      return "Span";
    }
  };

  static final ThriftAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new ThriftAdapter<DependencyLink>() {

    final Field PARENT = new Field(TYPE_STRING, 1);
    final Field CHILD = new Field(TYPE_STRING, 2);
    final Field CALL_COUNT = new Field(TYPE_I64, 4);
    final Field ERROR_COUNT = new Field(TYPE_I64, 5);

    @Override public DependencyLink read(ByteBuffer bytes) {
      DependencyLink.Builder result = DependencyLink.builder();
      Field field;

      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(PARENT)) {
          result.parent(readUtf8(bytes));
        } else if (field.isEqualTo(CHILD)) {
          result.child(readUtf8(bytes));
        } else if (field.isEqualTo(CALL_COUNT)) {
          result.callCount(bytes.getLong());
        } else if (field.isEqualTo(ERROR_COUNT)) {
          result.errorCount(bytes.getLong());
        } else {
          skip(bytes, field.type);
        }
      }

      return result.build();
    }

    @Override public int sizeInBytes(DependencyLink value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.parent);
      sizeInBytes += 3 + 4 + Buffer.utf8SizeInBytes(value.child);
      sizeInBytes += 3 + 8; // CALL_COUNT
      if (value.errorCount > 0) sizeInBytes += 3 + 8; // ERROR_COUNT
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override public void write(DependencyLink value, Buffer buffer) {
      PARENT.write(buffer);
      buffer.writeLengthPrefixed(value.parent);

      CHILD.write(buffer);
      buffer.writeLengthPrefixed(value.child);

      CALL_COUNT.write(buffer);
      buffer.writeLong(value.callCount);

      if (value.errorCount > 0) {
        ERROR_COUNT.write(buffer);
        buffer.writeLong(value.errorCount);
      }

      buffer.writeByte(TYPE_STOP);
    }

    @Override public String toString() {
      return "DependencyLink";
    }
  };

  @Override
  public DependencyLink readDependencyLink(byte[] bytes) {
    return read(DEPENDENCY_LINK_ADAPTER, ByteBuffer.wrap(bytes));
  }

  @Override
  public byte[] writeDependencyLink(DependencyLink value) {
    return write(DEPENDENCY_LINK_ADAPTER, value);
  }

  /**
   * Added for DataStax Cassandra driver, which returns data in ByteBuffers. The implementation
   * takes care not to re-buffer the data.
   *
   * @throws {@linkplain IllegalArgumentException} if the links couldn't be decoded
   */
  public List<DependencyLink> readDependencyLinks(ByteBuffer bytes) {
    return readList(DEPENDENCY_LINK_ADAPTER, bytes);
  }

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    return readList(DEPENDENCY_LINK_ADAPTER, ByteBuffer.wrap(bytes));
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    return writeList(DEPENDENCY_LINK_ADAPTER, value);
  }

  static <T> T read(ThriftReader<T> reader, ByteBuffer bytes) {
    checkArgument(bytes.remaining() > 0, "Empty input reading %s", reader);
    try {
      return reader.read(bytes);
    } catch (RuntimeException e) {
      throw exceptionReading(reader.toString(), e);
    }
  }

  /** Inability to encode is a programming bug. */
  static <T> byte[] write(Buffer.Writer<T> writer, T value) {
    Buffer buffer = new Buffer(writer.sizeInBytes(value));
    try {
      writer.write(value, buffer);
    } catch (RuntimeException e) {
      throw assertionError("Could not write " + value + " as TBinary", e);
    }
    return buffer.toByteArray();
  }

  static <T> List<T> readList(ThriftReader<T> reader, ByteBuffer bytes) {
    checkArgument(bytes.remaining() > 0, "Empty input reading List<%s>", reader);
    try {
      byte ignoredType = bytes.get();
      int length = guardLength(bytes, CONTAINER_LENGTH_LIMIT);
      if (length == 0) return Collections.emptyList();
      if (length == 1) return Collections.singletonList(reader.read(bytes));
      List<T> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        result.add(reader.read(bytes));
      }
      return result;
    } catch (RuntimeException e) {
      throw exceptionReading("List<" + reader + ">", e);
    }
  }

  static <T> byte[] writeList(Buffer.Writer<T> writer, List<T> value) {
    Buffer result = new Buffer(listSizeInBytes(writer, value));
    try {
      writeList(writer, value, result);
    } catch (RuntimeException e) {
      throw assertionError("Could not write " + value + " as TBinary", e);
    }
    return result.toByteArray();
  }

  static <T> void writeList(Buffer.Writer<T> writer, List<T> value, Buffer buffer) {
    int length = value.size();
    writeListBegin(buffer, length);
    for (int i = 0; i < length; i++) {
      writer.write(value.get(i), buffer);
    }
  }

  /** Encoding overhead is thrift type plus 32-bit length prefix */
  static <T> int listSizeInBytes(Buffer.Writer<T> writer, List<T> values) {
    int sizeInBytes = 5;
    for (int i = 0, length = values.size(); i < length; i++) {
      sizeInBytes += writer.sizeInBytes(values.get(i));
    }
    return sizeInBytes;
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (e instanceof EOFException) cause = "EOF";
    if (e instanceof IllegalStateException || e instanceof BufferUnderflowException) cause = "Malformed";
    String message = String.format("%s reading %s from TBinary", cause, type);
    throw new IllegalArgumentException(message, e);
  }

  static final class Field {
    final byte type;
    final int id;

    Field(byte type, int id) {
      this.type = type;
      this.id = id;
    }

    void write(Buffer buffer) {
      buffer.writeByte(type);
      buffer.writeShort(id);
    }

    static Field read(ByteBuffer bytes) {
      byte type = bytes.get();
      return new Field(type, type == TYPE_STOP ? TYPE_STOP : bytes.getShort());
    }

    boolean isEqualTo(Field that) {
      return this.type == that.type && this.id == that.id;
    }
  }

  static void skip(ByteBuffer bytes, byte type) {
    skip(bytes, type, MAX_SKIP_DEPTH);
  }

  static void skip(ByteBuffer bytes, byte type, int maxDepth) {
    if (maxDepth <= 0) throw new IllegalStateException("Maximum skip depth exceeded");
    switch (type) {
      case TYPE_BOOL:
      case TYPE_BYTE:
        skip(bytes, 1);
        break;
      case TYPE_I16:
        skip(bytes, 2);
        break;
      case TYPE_I32:
        skip(bytes, 4);
        break;
      case TYPE_DOUBLE:
      case TYPE_I64:
        skip(bytes, 8);
        break;
      case TYPE_STRING:
        int size = guardLength(bytes, STRING_LENGTH_LIMIT);
        skip(bytes, size);
        break;
      case TYPE_STRUCT:
        while (true) {
          Field field = Field.read(bytes);
          if (field.type == TYPE_STOP) return;
          skip(bytes, field.type, maxDepth - 1);
        }
      case TYPE_MAP:
        byte keyType = bytes.get();
        byte valueType = bytes.get();
        for (int i = 0, length = guardLength(bytes, CONTAINER_LENGTH_LIMIT); i < length; i++) {
          skip(bytes, keyType, maxDepth - 1);
          skip(bytes, valueType, maxDepth - 1);
        }
        break;
      case TYPE_SET:
      case TYPE_LIST:
        byte elemType = bytes.get();
        for (int i = 0, length = guardLength(bytes, CONTAINER_LENGTH_LIMIT); i < length; i++) {
          skip(bytes, elemType, maxDepth - 1);
        }
        break;
      default: // types that don't need explicit skipping
        break;
    }
  }

  static void skip(ByteBuffer bytes, int count) {
    bytes.position(bytes.position() + count);
  }

  static byte[] readByteArray(ByteBuffer bytes) {
    byte[] result = new byte[guardLength(bytes, STRING_LENGTH_LIMIT)];
    bytes.get(result);
    return result;
  }

  static String readUtf8(ByteBuffer bytes) {
    return new String(readByteArray(bytes), UTF_8);
  }

  static int guardLength(ByteBuffer bytes, int limit) {
    int length = bytes.getInt();
    if (length > limit) { // don't allocate massive arrays
      throw new IllegalStateException(length + " > " + limit + ": possibly malformed thrift");
    }
    return length;
  }

  static byte[] checkByteArrayLength(byte[] bytes, int limit) {
    if (bytes.length > limit) {
      throw new IllegalArgumentException(bytes.length + " > " + limit + ": byte array too long");
    }

    return bytes;
  }

  static String checkStringLength(String string, int limit) {
    int length = Buffer.utf8SizeInBytes(string);
    if (length > limit) {
      throw new IllegalArgumentException(length + " > " + limit + ": string too long");
    }
    return string;
  }

  static void writeListBegin(Buffer buffer, int size) {
    buffer.writeByte(TYPE_STRUCT);
    buffer.writeInt(size);
  }
}
