/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
import java.util.ArrayList;
import java.util.List;
import okio.Buffer;
import okio.ByteString;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;

import static zipkin.internal.Util.checkArgument;

/**
 * This is a hard-coded thrift codec, which allows us to include thrift marshalling in a minified
 * core jar. The hard coding not only keeps us with a single data-model, it also allows the minified
 * core jar free of SLFJ classes otherwise included in generated types.
 *
 * <p/> This is an Okio-native TBinaryProtocol codec. Natively doing this reduces dependencies and
 * array duplication.
 */
public final class ThriftCodec implements Codec {
  // break vs decode huge structs, like > 1MB strings or 10k spans in a trace.
  private static final int STRING_LENGTH_LIMIT = 1 * 1024 * 1024;
  private static final int CONTAINER_LENGTH_LIMIT = 10 * 1000;
  // break vs recursing infinitely when skipping data
  private static int MAX_SKIP_DEPTH = 2147483647;

  // taken from org.apache.thrift.protocol.TType
  private static final byte TYPE_STOP = 0;
  private static final byte TYPE_BOOL = 2;
  private static final byte TYPE_BYTE = 3;
  private static final byte TYPE_DOUBLE = 4;
  private static final byte TYPE_I16 = 6;
  private static final byte TYPE_I32 = 8;
  private static final byte TYPE_I64 = 10;
  private static final byte TYPE_STRING = 11;
  private static final byte TYPE_STRUCT = 12;
  private static final byte TYPE_MAP = 13;
  private static final byte TYPE_SET = 14;
  private static final byte TYPE_LIST = 15;

  @Override
  public Span readSpan(byte[] bytes) {
    return read(SPAN_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SPAN_ADAPTER, value);
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
    return read(SPANS_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpans(List<Span> value) {
    return write(SPANS_ADAPTER, value);
  }

  @Override
  public byte[] writeTraces(List<List<Span>> value) {
    return write(TRACES_ADAPTER, value);
  }

  interface ThriftWriter<T> {
    void write(T value, Buffer buffer);
  }

  interface ThriftReader<T> {
    T read(Buffer buffer) throws EOFException;
  }

  interface ThriftAdapter<T> extends ThriftReader<T>, ThriftWriter<T> {
  }

  static final ThriftAdapter<Endpoint> ENDPOINT_ADAPTER = new ThriftAdapter<Endpoint>() {

    final Field IPV4 = new Field(TYPE_I32, 1);
    final Field PORT = new Field(TYPE_I16, 2);
    final Field SERVICE_NAME = new Field(TYPE_STRING, 3);

    @Override
    public Endpoint read(Buffer buffer) throws EOFException {
      Endpoint.Builder result = new Endpoint.Builder();
      Field field;

      while (true) {
        field = Field.read(buffer);
        if (field.type == TYPE_STOP) break;

        if (field.equals(IPV4)) {
          result.ipv4(buffer.readInt());
        } else if (field.equals(PORT)) {
          result.port(buffer.readShort());
        } else if (field.equals(SERVICE_NAME)) {
          result.serviceName(readUtf8(buffer));
        } else {
          skip(buffer, field.type);
        }
      }
      return result.build();
    }

    @Override
    public void write(Endpoint value, Buffer buffer) {
      IPV4.write(buffer);
      buffer.writeInt(value.ipv4);

      PORT.write(buffer);
      buffer.writeShort(value.port == null ? 0 : value.port);

      SERVICE_NAME.write(buffer);
      writeUtf8(buffer, value.serviceName);

      buffer.writeByte(TYPE_STOP);
    }
  };

  static final ThriftAdapter<Annotation> ANNOTATION_ADAPTER = new ThriftAdapter<Annotation>() {

    final Field TIMESTAMP = new Field(TYPE_I64, 1);
    final Field VALUE = new Field(TYPE_STRING, 2);
    final Field ENDPOINT = new Field(TYPE_STRUCT, 3);

    @Override
    public Annotation read(Buffer buffer) throws EOFException {
      Annotation.Builder result = new Annotation.Builder();
      Field field;
      while (true) {
        field = Field.read(buffer);
        if (field.type == TYPE_STOP) break;

        if (field.equals(TIMESTAMP)) {
          result.timestamp(buffer.readLong());
        } else if (field.equals(VALUE)) {
          result.value(readUtf8(buffer));
        } else if (field.equals(ENDPOINT)) {
          result.endpoint(ENDPOINT_ADAPTER.read(buffer));
        } else {
          skip(buffer, field.type);
        }
      }
      return result.build();
    }

    @Override
    public void write(Annotation value, Buffer buffer) {
      TIMESTAMP.write(buffer);
      buffer.writeLong(value.timestamp);

      if (value.value != null) {
        VALUE.write(buffer);
        writeUtf8(buffer, value.value);
      }

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

    @Override
    public BinaryAnnotation read(Buffer buffer) throws EOFException {
      BinaryAnnotation.Builder result = new BinaryAnnotation.Builder();
      Field field;

      while (true) {
        field = Field.read(buffer);
        if (field.type == TYPE_STOP) break;

        if (field.equals(KEY)) {
          result.key(readUtf8(buffer));
        } else if (field.equals(VALUE)) {
          result.value(readBytes(buffer));
        } else if (field.equals(TYPE)) {
          result.type(BinaryAnnotation.Type.fromValue(buffer.readInt()));
        } else if (field.equals(ENDPOINT)) {
          result.endpoint(ENDPOINT_ADAPTER.read(buffer));
        } else {
          skip(buffer, field.type);
        }
      }
      return result.build();
    }

    @Override
    public void write(BinaryAnnotation value, Buffer buffer) {
      KEY.write(buffer);
      writeUtf8(buffer, value.key);

      VALUE.write(buffer);
      buffer.writeInt(value.value.length);
      buffer.write(value.value);

      TYPE.write(buffer);
      buffer.writeInt(value.type.value);

      if (value.endpoint != null) {
        ENDPOINT.write(buffer);
        ENDPOINT_ADAPTER.write(value.endpoint, buffer);
      }

      buffer.writeByte(TYPE_STOP);
    }
  };

  static final ThriftAdapter<List<Annotation>> ANNOTATIONS_ADAPTER = new ListAdapter<>(ANNOTATION_ADAPTER);
  static final ThriftAdapter<List<BinaryAnnotation>> BINARY_ANNOTATIONS_ADAPTER = new ListAdapter<>(BINARY_ANNOTATION_ADAPTER);

  static final ThriftAdapter<Span> SPAN_ADAPTER = new ThriftAdapter<Span>() {

    final Field TRACE_ID = new Field(TYPE_I64, 1);
    final Field NAME = new Field(TYPE_STRING, 3);
    final Field ID = new Field(TYPE_I64, 4);
    final Field PARENT_ID = new Field(TYPE_I64, 5);
    final Field ANNOTATIONS = new Field(TYPE_LIST, 6);
    final Field BINARY_ANNOTATIONS = new Field(TYPE_LIST, 8);
    final Field DEBUG = new Field(TYPE_BOOL, 9);
    final Field TIMESTAMP = new Field(TYPE_I64, 10);
    final Field DURATION = new Field(TYPE_I64, 11);

    @Override
    public Span read(Buffer buffer) throws EOFException {
      Span.Builder result = new Span.Builder();
      Field field;

      while (true) {
        field = Field.read(buffer);
        if (field.type == TYPE_STOP) break;

        if (field.equals(TRACE_ID)) {
          result.traceId(buffer.readLong());
        } else if (field.equals(NAME)) {
          result.name(readUtf8(buffer));
        } else if (field.equals(ID)) {
          result.id(buffer.readLong());
        } else if (field.equals(PARENT_ID)) {
          result.parentId(buffer.readLong());
        } else if (field.equals(ANNOTATIONS)) {
          result.annotations(ANNOTATIONS_ADAPTER.read(buffer));
        } else if (field.equals(BINARY_ANNOTATIONS)) {
          result.binaryAnnotations(BINARY_ANNOTATIONS_ADAPTER.read(buffer));
        } else if (field.equals(DEBUG)) {
          result.debug(buffer.readByte() == 1);
        } else if (field.equals(TIMESTAMP)) {
          result.timestamp(buffer.readLong());
        } else if (field.equals(DURATION)) {
          result.duration(buffer.readLong());
        } else {
          skip(buffer, field.type);
        }
      }

      return result.build();
    }

    @Override
    public void write(Span value, Buffer buffer) {

      TRACE_ID.write(buffer);
      buffer.writeLong(value.traceId);

      NAME.write(buffer);
      writeUtf8(buffer, value.name);

      ID.write(buffer);
      buffer.writeLong(value.id);

      if (value.parentId != null) {
        PARENT_ID.write(buffer);
        buffer.writeLong(value.parentId);
      }

      ANNOTATIONS.write(buffer);
      ANNOTATIONS_ADAPTER.write(value.annotations, buffer);

      BINARY_ANNOTATIONS.write(buffer);
      BINARY_ANNOTATIONS_ADAPTER.write(value.binaryAnnotations, buffer);

      if (value.debug != null) {
        DEBUG.write(buffer);
        buffer.writeByte(value.debug ? 1 : 0);
      }

      if (value.timestamp != null) {
        TIMESTAMP.write(buffer);
        buffer.writeLong(value.timestamp);
      }

      if (value.duration != null) {
        DURATION.write(buffer);
        buffer.writeLong(value.duration);
      }

      buffer.writeByte(TYPE_STOP);
    }

    @Override
    public String toString() {
      return "Span";
    }
  };

  static final ThriftAdapter<List<Span>> SPANS_ADAPTER = new ListAdapter<>(SPAN_ADAPTER);
  static final ThriftAdapter<List<List<Span>>> TRACES_ADAPTER = new ListAdapter<>(SPANS_ADAPTER);

  static final ThriftAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new ThriftAdapter<DependencyLink>() {

    final Field PARENT = new Field(TYPE_STRING, 1);
    final Field CHILD = new Field(TYPE_STRING, 2);
    final Field CALL_COUNT = new Field(TYPE_I64, 4);

    @Override
    public DependencyLink read(Buffer buffer) throws EOFException {
      DependencyLink.Builder result = new DependencyLink.Builder();
      Field field;

      while (true) {
        field = Field.read(buffer);
        if (field.type == TYPE_STOP) break;

        if (field.equals(PARENT)) {
          result.parent(readUtf8(buffer));
        } else if (field.equals(CHILD)) {
          result.child(readUtf8(buffer));
        } else if (field.equals(CALL_COUNT)) {
          result.callCount(buffer.readLong());
        } else {
          skip(buffer, field.type);
        }
      }

      return result.build();
    }

    @Override
    public void write(DependencyLink value, Buffer buffer) {
      PARENT.write(buffer);
      writeUtf8(buffer, value.parent);

      CHILD.write(buffer);
      writeUtf8(buffer, value.child);

      CALL_COUNT.write(buffer);
      buffer.writeLong(value.callCount);

      buffer.writeByte(TYPE_STOP);
    }

    @Override
    public String toString() {
      return "DependencyLink";
    }
  };

  static final ThriftAdapter<List<DependencyLink>> DEPENDENCY_LINKS_ADAPTER = new ListAdapter<>(DEPENDENCY_LINK_ADAPTER);

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    return read(DEPENDENCY_LINKS_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    return write(DEPENDENCY_LINKS_ADAPTER, value);
  }

  static <T> T read(ThriftReader<T> reader, byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading %s", reader);
    try {
      return reader.read(new Buffer().write(bytes));
    } catch (EOFException | RuntimeException e) {
      throw exceptionReading(reader.toString(), bytes, e);
    }
  }

  /** Inability to encode is a programming bug. */
  static <T> byte[] write(ThriftWriter<T> writer, T value) {
    Buffer buffer = new Buffer();
    try {
      writer.write(value, buffer);
    } catch (RuntimeException e) {
      throw new AssertionError("Could not write " + value + " as TBinary", e);
    }
    return buffer.readByteArray();
  }

  static <T> List<T> readList(ThriftReader<T> reader, Buffer buffer) throws EOFException {
    byte ignoredType = buffer.readByte();
    int length = guardLength(buffer, CONTAINER_LENGTH_LIMIT);
    List<T> result = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      result.add(reader.read(buffer));
    }
    return result;
  }

  static <T> void writeList(ThriftWriter<T> writer, List<T> value, Buffer buffer) {
    writeListBegin(buffer, value.size());
    for (int i = 0, length = value.size(); i < length; i++) {
      writer.write(value.get(i), buffer);
    }
  }

  static final class ListAdapter<T> implements ThriftAdapter<List<T>> {
    final ThriftAdapter<T> adapter;

    ListAdapter(ThriftAdapter<T> adapter) {
      this.adapter = adapter;
    }

    @Override
    public List<T> read(Buffer buffer) throws EOFException {
      return readList(adapter, buffer);
    }

    @Override
    public void write(List<T> value, Buffer buffer) {
      writeList(adapter, value, buffer);
    }

    @Override
    public String toString() {
      return "List<" + adapter + ">";
    }
  }

  static IllegalArgumentException exceptionReading(String type, byte[] bytes, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (e instanceof EOFException) cause = "EOF";
    if (e instanceof IllegalStateException) cause = "Malformed";
    String message =
        String.format("%s reading %s from TBinary: %s", cause, type, ByteString.of(bytes).base64());
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

    static Field read(Buffer buffer) {
      byte type = buffer.readByte();
      return new Field(type, type == TYPE_STOP ? TYPE_STOP : buffer.readShort());
    }

    boolean equals(Field that) {
      return this.type == that.type && this.id == that.id;
    }
  }

  static void skip(Buffer buffer, byte type) throws EOFException {
    skip(buffer, type, MAX_SKIP_DEPTH);
  }

  static void skip(Buffer buffer, byte type, int maxDepth) throws EOFException {
    if (maxDepth <= 0) throw new EOFException("Maximum skip depth exceeded");
    switch (type) {
      case TYPE_BOOL:
      case TYPE_BYTE:
        buffer.skip(1);
        break;
      case TYPE_I16:
        buffer.skip(2);
        break;
      case TYPE_I32:
        buffer.skip(4);
        break;
      case TYPE_DOUBLE:
      case TYPE_I64:
        buffer.skip(8);
        break;
      case TYPE_STRING:
        int size = guardLength(buffer, STRING_LENGTH_LIMIT);
        buffer.skip(size);
        break;
      case TYPE_STRUCT:
        while (true) {
          Field field = Field.read(buffer);
          if (field.type == TYPE_STOP) return;
          skip(buffer, field.type, maxDepth - 1);
        }
      case TYPE_MAP:
        byte keyType = buffer.readByte();
        byte valueType = buffer.readByte();
        for (int i = 0, length = guardLength(buffer, CONTAINER_LENGTH_LIMIT); i < length; i++) {
          skip(buffer, keyType, maxDepth - 1);
          skip(buffer, valueType, maxDepth - 1);
        }
        break;
      case TYPE_SET:
      case TYPE_LIST:
        byte elemType = buffer.readByte();
        for (int i = 0, length = guardLength(buffer, CONTAINER_LENGTH_LIMIT); i < length; i++) {
          skip(buffer, elemType, maxDepth - 1);
        }
        break;
      default: // types that don't need explicit skipping
        break;
    }
  }

  static byte[] readBytes(Buffer buffer) throws EOFException {
    return buffer.readByteArray(guardLength(buffer, STRING_LENGTH_LIMIT));
  }

  static String readUtf8(Buffer buffer) throws EOFException {
    return buffer.readUtf8(guardLength(buffer, STRING_LENGTH_LIMIT));
  }

  static int guardLength(Buffer buffer, int limit) {
    int length = buffer.readInt();
    if (length > limit) { // don't allocate massive arrays
      throw new IllegalStateException(length + " > " + limit + ": possibly malformed thrift");
    }
    return length;
  }

  static void writeListBegin(Buffer buffer, int size) {
    buffer.writeByte(TYPE_STRUCT);
    buffer.writeInt(size);
  }

  static void writeUtf8(Buffer buffer, String string) {
    Buffer temp = new Buffer().writeUtf8(string);
    buffer.writeInt((int) temp.size());
    buffer.write(temp, temp.size());
  }
}
