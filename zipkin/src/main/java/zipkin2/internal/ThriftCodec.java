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

import java.io.EOFException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import zipkin2.Span;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static zipkin2.internal.ThriftField.TYPE_BOOL;
import static zipkin2.internal.ThriftField.TYPE_BYTE;
import static zipkin2.internal.ThriftField.TYPE_DOUBLE;
import static zipkin2.internal.ThriftField.TYPE_I16;
import static zipkin2.internal.ThriftField.TYPE_I32;
import static zipkin2.internal.ThriftField.TYPE_I64;
import static zipkin2.internal.ThriftField.TYPE_LIST;
import static zipkin2.internal.ThriftField.TYPE_MAP;
import static zipkin2.internal.ThriftField.TYPE_SET;
import static zipkin2.internal.ThriftField.TYPE_STOP;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.ThriftField.TYPE_STRUCT;

// @Immutable
public final class ThriftCodec {
  // break vs recursing infinitely when skipping data
  static final int MAX_SKIP_DEPTH = 2147483647;

  final V1ThriftSpanWriter writer = new V1ThriftSpanWriter();

  public int sizeInBytes(Span input) {
    return writer.sizeInBytes(input);
  }

  public byte[] write(Span span) {
    return writer.write(span);
  }

  /** Encoding overhead is thrift type plus 32-bit length prefix */
  static <T> int listSizeInBytes(WriteBuffer.Writer<T> writer, List<T> values) {
    int sizeInBytes = 5;
    for (int i = 0, length = values.size(); i < length; i++) {
      sizeInBytes += writer.sizeInBytes(values.get(i));
    }
    return sizeInBytes;
  }

  public static boolean read(ReadBuffer buffer, Collection<Span> out) {
    if (buffer.available() == 0) return false;
    try {
      V1Span v1Span = new V1ThriftSpanReader().read(buffer);
      V1SpanConverter.create().convert(v1Span, out);
      return true;
    } catch (Exception e) {
      throw exceptionReading("Span", e);
    }
  }

  @Nullable
  public static Span readOne(ReadBuffer buffer) {
    if (buffer.available() == 0) return null;
    try {
      V1Span v1Span = new V1ThriftSpanReader().read(buffer);
      List<Span> out = new ArrayList<>(1);
      V1SpanConverter.create().convert(v1Span, out);
      return out.get(0);
    } catch (Exception e) {
      throw exceptionReading("Span", e);
    }
  }

  public static boolean readList(ReadBuffer buffer, Collection<Span> out) {
    int length = buffer.available();
    if (length == 0) return false;
    try {
      int listLength = readListLength(buffer);
      if (listLength == 0) return false;
      V1ThriftSpanReader reader = new V1ThriftSpanReader();
      V1SpanConverter converter = V1SpanConverter.create();
      for (int i = 0; i < listLength; i++) {
        V1Span v1Span = reader.read(buffer);
        converter.convert(v1Span, out);
      }
    } catch (Exception e) {
      throw exceptionReading("List<Span>", e);
    }
    return true;
  }

  static int readListLength(ReadBuffer buffer) {
    buffer.readByte(); // we ignore the type
    return buffer.readInt();
  }

  static <T> void writeList(WriteBuffer.Writer<T> writer, List<T> value, WriteBuffer buffer) {
    int length = value.size();
    writeListBegin(buffer, length);
    for (int i = 0; i < length; i++) {
      writer.write(value.get(i), buffer);
    }
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (e instanceof EOFException) cause = "EOF";
    if (e instanceof IllegalStateException || e instanceof BufferUnderflowException) {
      cause = "Malformed";
    }
    String message = String.format("%s reading %s from TBinary", cause, type);
    throw new IllegalArgumentException(message, e);
  }

  static void skip(ReadBuffer buffer, byte type) {
    skip(buffer, type, MAX_SKIP_DEPTH);
  }

  static void skip(ReadBuffer buffer, byte type, int maxDepth) {
    if (maxDepth <= 0) throw new IllegalStateException("Maximum skip depth exceeded");
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
        buffer.skip(buffer.readInt());
        break;
      case TYPE_STRUCT:
        while (true) {
          ThriftField thriftField = ThriftField.read(buffer);
          if (thriftField.type == TYPE_STOP) return;
          skip(buffer, thriftField.type, maxDepth - 1);
        }
      case TYPE_MAP:
        byte keyType = buffer.readByte();
        byte valueType = buffer.readByte();
        for (int i = 0, length = buffer.readInt(); i < length; i++) {
          skip(buffer, keyType, maxDepth - 1);
          skip(buffer, valueType, maxDepth - 1);
        }
        break;
      case TYPE_SET:
      case TYPE_LIST:
        byte elemType = buffer.readByte();
        for (int i = 0, length = buffer.readInt(); i < length; i++) {
          skip(buffer, elemType, maxDepth - 1);
        }
        break;
      default: // types that don't need explicit skipping
        break;
    }
  }

  static void writeListBegin(WriteBuffer buffer, int size) {
    buffer.writeByte(TYPE_STRUCT);
    writeInt(buffer, size);
  }

  static void writeLengthPrefixed(WriteBuffer buffer, String utf8) {
    writeInt(buffer, WriteBuffer.utf8SizeInBytes(utf8));
    buffer.writeUtf8(utf8);
  }

  static void writeInt(WriteBuffer buf, int v) {
    buf.writeByte((byte) ((v >>> 24L) & 0xff));
    buf.writeByte((byte) ((v >>> 16L) & 0xff));
    buf.writeByte((byte) ((v >>> 8L) & 0xff));
    buf.writeByte((byte) (v & 0xff));
  }

  static void writeLong(WriteBuffer buf, long v) {
    buf.writeByte((byte) ((v >>> 56L) & 0xff));
    buf.writeByte((byte) ((v >>> 48L) & 0xff));
    buf.writeByte((byte) ((v >>> 40L) & 0xff));
    buf.writeByte((byte) ((v >>> 32L) & 0xff));
    buf.writeByte((byte) ((v >>> 24L) & 0xff));
    buf.writeByte((byte) ((v >>> 16L) & 0xff));
    buf.writeByte((byte) ((v >>> 8L) & 0xff));
    buf.writeByte((byte) (v & 0xff));
  }
}
