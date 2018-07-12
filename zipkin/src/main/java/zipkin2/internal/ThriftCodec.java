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

import java.io.EOFException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import zipkin2.Span;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static zipkin2.internal.Buffer.utf8SizeInBytes;
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
  static final Charset UTF_8 = Charset.forName("UTF-8");
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
  static <T> int listSizeInBytes(Buffer.Writer<T> writer, List<T> values) {
    int sizeInBytes = 5;
    for (int i = 0, length = values.size(); i < length; i++) {
      sizeInBytes += writer.sizeInBytes(values.get(i));
    }
    return sizeInBytes;
  }

  public static boolean read(byte[] bytes, Collection<Span> out) {
    if (bytes.length == 0) return false;
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    try {
      V1Span v1Span = new V1ThriftSpanReader().read(buffer);
      V1SpanConverter.create().convert(v1Span, out);
      return true;
    } catch (Exception e) {
      throw exceptionReading("Span", e);
    }
  }

  @Nullable
  public static Span readOne(byte[] bytes) {
    if (bytes.length == 0) return null;
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    try {
      V1Span v1Span = new V1ThriftSpanReader().read(buffer);
      List<Span> out = new ArrayList<>(1);
      V1SpanConverter.create().convert(v1Span, out);
      return out.get(0);
    } catch (Exception e) {
      throw exceptionReading("Span", e);
    }
  }

  public static boolean readList(byte[] bytes, Collection<Span> out) {
    int length = bytes.length;
    if (length == 0) return false;
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
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

  static int readListLength(ByteBuffer bytes) {
    byte ignoredType = bytes.get();
    return guardLength(bytes);
  }

  static <T> void writeList(Buffer.Writer<T> writer, List<T> value, Buffer buffer) {
    int length = value.size();
    writeListBegin(buffer, length);
    for (int i = 0; i < length; i++) {
      writer.write(value.get(i), buffer);
    }
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (e instanceof EOFException) cause = "EOF";
    if (e instanceof IllegalStateException || e instanceof BufferUnderflowException)
      cause = "Malformed";
    String message = String.format("%s reading %s from TBinary", cause, type);
    throw new IllegalArgumentException(message, e);
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
        int size = guardLength(bytes);
        skip(bytes, size);
        break;
      case TYPE_STRUCT:
        while (true) {
          ThriftField thriftField = ThriftField.read(bytes);
          if (thriftField.type == TYPE_STOP) return;
          skip(bytes, thriftField.type, maxDepth - 1);
        }
      case TYPE_MAP:
        byte keyType = bytes.get();
        byte valueType = bytes.get();
        for (int i = 0, length = guardLength(bytes); i < length; i++) {
          skip(bytes, keyType, maxDepth - 1);
          skip(bytes, valueType, maxDepth - 1);
        }
        break;
      case TYPE_SET:
      case TYPE_LIST:
        byte elemType = bytes.get();
        for (int i = 0, length = guardLength(bytes); i < length; i++) {
          skip(bytes, elemType, maxDepth - 1);
        }
        break;
      default: // types that don't need explicit skipping
        break;
    }
  }

  static void skip(ByteBuffer bytes, int count) {
    // avoid java.lang.NoSuchMethodError: java.nio.ByteBuffer.position(I)Ljava/nio/ByteBuffer;
    // bytes.position(bytes.position() + count);
    for (int i = 0; i< count && bytes.hasRemaining(); i++) {
      bytes.get();
    }
  }

  static byte[] readByteArray(ByteBuffer bytes) {
    byte[] result = new byte[guardLength(bytes)];
    bytes.get(result);
    return result;
  }

  static String readUtf8(ByteBuffer bytes) {
    return new String(readByteArray(bytes), UTF_8);
  }

  static int guardLength(ByteBuffer buffer) {
    int length = buffer.getInt();
    if (length > buffer.remaining()) {
      throw new IllegalArgumentException(
          "Truncated: length " + length + " > bytes remaining " + buffer.remaining());
    }
    return length;
  }

  static void writeListBegin(Buffer buffer, int size) {
    buffer.writeByte(TYPE_STRUCT);
    writeInt(buffer, size);
  }

  static void writeLengthPrefixed(Buffer buffer, String utf8) {
    int ignoredLength = utf8SizeInBytes(utf8);
    writeInt(buffer, utf8SizeInBytes(utf8));
    buffer.writeUtf8(utf8);
  }

  static void writeInt(Buffer buf, int v) {
    buf.writeByte((byte) ((v >>> 24L) & 0xff));
    buf.writeByte((byte) ((v >>> 16L) & 0xff));
    buf.writeByte((byte) ((v >>> 8L) & 0xff));
    buf.writeByte((byte) (v & 0xff));
  }

  static void writeLong(Buffer buf, long v) {
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
