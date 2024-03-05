/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

final class ThriftField {
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

  final byte type;
  final int id;

  ThriftField(byte type, int id) {
    this.type = type;
    this.id = id;
  }

  void write(WriteBuffer buffer) {
    buffer.writeByte(type);
    // Write ID as a short!
    buffer.writeByte((id >>> 8L) & 0xff);
    buffer.writeByte(id & 0xff);
  }

  static ThriftField read(ReadBuffer bytes) {
    byte type = bytes.readByte();
    return new ThriftField(type, type == TYPE_STOP ? TYPE_STOP : bytes.readShort());
  }

  boolean isEqualTo(ThriftField that) {
    return this.type == that.type && this.id == that.id;
  }
}
