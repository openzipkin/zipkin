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

import static zipkin2.internal.JsonCodec.UTF_8;

/**
 * Everything here assumes the field numbers are less than 16, implying a 1 byte tag.
 */
//@Immutable
final class Proto3Fields {
  /**
   * Define the wire types, except the deprecated ones (groups)
   *
   * <p>See https://developers.google.com/protocol-buffers/docs/encoding#structure
   */
  static final int
    WIRETYPE_VARINT = 0,
    WIRETYPE_FIXED64 = 1,
    WIRETYPE_LENGTH_DELIMITED = 2,
    WIRETYPE_FIXED32 = 5;

  static class Field {
    final int fieldNumber;
    final int wireType;
    /**
     * "Each key in the streamed message is a varint with the value {@code (field_number << 3) | wire_type}"
     *
     * <p>See https://developers.google.com/protocol-buffers/docs/encoding#structure
     */
    final int key;

    Field(int key) {
      this(key >>> 3, key & (1 << 3) - 1, key);
    }

    Field(int fieldNumber, int wireType, int key) {
      this.fieldNumber = fieldNumber;
      this.wireType = wireType;
      this.key = key;
    }

    static int fieldNumber(int key, int byteL) {
      int fieldNumber = key >>> 3;
      if (fieldNumber != 0) return fieldNumber;
      throw new IllegalArgumentException("Malformed: fieldNumber was zero at byte " + byteL);
    }

    static int wireType(int key, int byteL) {
      int wireType = key & (1 << 3) - 1;
      if (wireType != 0 && wireType != 1 && wireType != 2 && wireType != 5) {
        throw new IllegalArgumentException(
          "Malformed: invalid wireType " + wireType + " at byte " + byteL);
      }
      return wireType;
    }

    static boolean skipValue(Buffer buffer, int wireType) {
      int remaining = buffer.remaining();
      switch (wireType) {
        case WIRETYPE_VARINT:
          for (int i = 0; i < remaining; i++) {
            if (buffer.readByte() >= 0) return true;
          }
          return false;
        case WIRETYPE_FIXED64:
          return buffer.skip(8);
        case WIRETYPE_LENGTH_DELIMITED:
          int length = buffer.readVarint32();
          return buffer.skip(length);
        case WIRETYPE_FIXED32:
          return buffer.skip(4);
        default:
          throw new IllegalArgumentException(
            "Malformed: invalid wireType " + wireType + " at byte " + buffer.pos);
      }
    }
  }

  /**
   * Leniently skips out null, but not on empty string, allowing tag "error" -> "" to serialize
   * properly.
   *
   * <p>This won't result in empty {@link zipkin2.Span#name()} or {@link Endpoint#serviceName()}
   * because in both cases constructors coerce empty values to null.
   */
  static abstract class LengthDelimitedField<T> extends Field {
    LengthDelimitedField(int key) {
      super(key);
      assert wireType == WIRETYPE_LENGTH_DELIMITED;
    }

    final int sizeInBytes(T value) {
      if (value == null) return 0;
      int sizeOfValue = sizeOfValue(value);
      return sizeOfLengthDelimitedField(sizeOfValue);
    }

    final void write(Buffer b, T value) {
      if (value == null) return;
      int sizeOfValue = sizeOfValue(value);
      b.writeByte(key);
      b.writeVarint(sizeOfValue); // length prefix
      writeValue(b, value);
    }

    /**
     * Calling this after consuming the field key to ensures there's enough space for the data. Null
     * is returned when the length prefix is zero.
     */
    final T readLengthPrefixAndValue(Buffer b) {
      int length = readLengthPrefix(b);
      if (length == 0) return null;
      return readValue(b, length);
    }

    final int readLengthPrefix(Buffer b) {
      int length = b.readVarint32();
      Proto3Fields.ensureLength(b, length);
      return length;
    }

    abstract int sizeOfValue(T value);

    abstract void writeValue(Buffer b, T value);

    /** @param length is greater than zero */
    abstract T readValue(Buffer b, int length);
  }

  static class BytesField extends LengthDelimitedField<byte[]> {
    BytesField(int key) {
      super(key);
    }

    @Override int sizeOfValue(byte[] bytes) {
      return bytes.length;
    }

    @Override void writeValue(Buffer b, byte[] bytes) {
      b.write(bytes);
    }

    @Override byte[] readValue(Buffer b, int length) {
      byte[] result = new byte[length];
      System.arraycopy(b.toByteArray(), b.pos, result, 0, length);
      b.pos += length;
      return result;
    }
  }

  static class HexField extends LengthDelimitedField<String> {
    static final char[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    HexField(int key) {
      super(key);
    }

    @Override int sizeOfValue(String hex) {
      if (hex == null) return 0;
      return hex.length() / 2;
    }

    @Override void writeValue(Buffer b, String hex) {
      // similar logic to okio.ByteString.decodeHex
      for (int i = 0, length = hex.length(); i < length; i++) {
        int d1 = decodeLowerHex(hex.charAt(i++)) << 4;
        int d2 = decodeLowerHex(hex.charAt(i));
        b.writeByte((byte) (d1 + d2));
      }
    }

    static int decodeLowerHex(char c) {
      if (c >= '0' && c <= '9') return c - '0';
      if (c >= 'a' && c <= 'f') return c - 'a' + 10;
      throw new AssertionError("not lowerHex " + c); // bug
    }

    @Override String readValue(Buffer buffer, int length) {
      length *= 2;
      char[] result = new char[length];

      for (int i = 0; i < length; i += 2) {
        byte b = buffer.readByte();
        result[i + 0] = HEX_DIGITS[(b >> 4) & 0xf];
        result[i + 1] = HEX_DIGITS[b & 0xf];
      }

      return new String(result);
    }
  }

  static class Utf8Field extends LengthDelimitedField<String> {
    Utf8Field(int key) {
      super(key);
    }

    @Override int sizeOfValue(String utf8) {
      return utf8 != null ? Buffer.utf8SizeInBytes(utf8) : 0;
    }

    @Override void writeValue(Buffer b, String utf8) {
      b.writeUtf8(utf8);
    }

    @Override String readValue(Buffer buffer, int length) {
      String result = new String(buffer.toByteArray(), buffer.pos, length, UTF_8);
      buffer.pos += length;
      return result;
    }
  }

  static final class Fixed64Field extends Field {
    Fixed64Field(int key) {
      super(key);
      assert wireType == WIRETYPE_FIXED64;
    }

    void write(Buffer b, long number) {
      if (number == 0) return;
      b.writeByte(key);
      b.writeLongLe(number);
    }

    int sizeInBytes(long number) {
      if (number == 0) return 0;
      return 1 + 8; // tag + 8 byte number
    }

    long readValue(Buffer buffer) {
      ensureLength(buffer, 8);
      return buffer.readLongLe();
    }
  }

  static class VarintField extends Field {
    VarintField(int key) {
      super(key);
      assert wireType == WIRETYPE_VARINT;
    }

    int sizeInBytes(int number) {
      return number != 0 ? 1 + Buffer.varintSizeInBytes(number) : 0; // tag + varint
    }

    void write(Buffer b, int number) {
      if (number == 0) return;
      b.writeByte(key);
      b.writeVarint(number);
    }

    int sizeInBytes(long number) {
      return number != 0 ? 1 + Buffer.varintSizeInBytes(number) : 0; // tag + varint
    }

    void write(Buffer b, long number) {
      if (number == 0) return;
      b.writeByte(key);
      b.writeVarint(number);
    }
  }

  static final class BooleanField extends Field {
    BooleanField(int key) {
      super(key);
      assert wireType == WIRETYPE_VARINT;
    }

    int sizeInBytes(boolean bool) {
      return bool ? 2 : 0; // tag + varint
    }

    void write(Buffer b, boolean bool) {
      if (!bool) return;
      b.writeByte(key);
      b.writeByte(1);
    }

    boolean read(Buffer b) {
      byte bool = b.readByte();
      if (bool < 0 || bool > 1) {
        throw new IllegalArgumentException("Malformed: invalid boolean value at byte " + b.pos);
      }
      return bool == 1;
    }
  }

  // added for completion as later we will skip fields we don't use
  static final class Fixed32Field extends Field {
    Fixed32Field(int key) {
      super(key);
      assert wireType == WIRETYPE_FIXED32;
    }

    int sizeInBytes(int number) {
      if (number == 0) return 0;
      return 1 + 4; // tag + 4 byte number
    }
  }

  static int sizeOfLengthDelimitedField(int sizeInBytes) {
    return 1 + Buffer.varintSizeInBytes(sizeInBytes) + sizeInBytes; // tag + len + bytes
  }

  static void ensureLength(Buffer buffer, int length) {
    if (length > buffer.remaining()) {
      throw new IllegalArgumentException(
        "Truncated: length " + length + " > bytes remaining " + buffer.remaining());
    }
  }
}
