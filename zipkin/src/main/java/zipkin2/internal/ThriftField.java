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
