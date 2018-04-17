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

import org.junit.Test;
import zipkin2.internal.Proto3Fields.BooleanField;
import zipkin2.internal.Proto3Fields.BytesField;
import zipkin2.internal.Proto3Fields.Fixed64Field;
import zipkin2.internal.Proto3Fields.MapEntryField;
import zipkin2.internal.Proto3Fields.Utf8Field;
import zipkin2.internal.Proto3Fields.VarintField;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin2.internal.Proto3Fields.Field;
import static zipkin2.internal.Proto3Fields.Fixed32Field;
import static zipkin2.internal.Proto3Fields.HexField;
import static zipkin2.internal.Proto3Fields.WIRETYPE_FIXED32;
import static zipkin2.internal.Proto3Fields.WIRETYPE_FIXED64;
import static zipkin2.internal.Proto3Fields.WIRETYPE_LENGTH_DELIMITED;
import static zipkin2.internal.Proto3Fields.WIRETYPE_VARINT;

public class Proto3FieldsTest {
  Buffer buf = new Buffer(2048); // bigger than needed to test sizeOf

  /** Shows we can reliably look at a byte zero to tell if we are decoding proto3 repeated fields. */
  @Test public void field_key_fieldOneLengthDelimited() {
    Field field = new Field(1, WIRETYPE_LENGTH_DELIMITED);
    assertThat(field.key)
      .isEqualTo(0b00001010) // (field_number << 3) | wire_type = 1 << 3 | 2
      .isEqualTo(10); // for sanity of those looking at debugger, 4th bit + 2nd bit = 10
  }

  @Test public void varint_sizeInBytes() {
    VarintField field = new VarintField(1);

    assertThat(field.sizeInBytes(0))
      .isZero();
    assertThat(field.sizeInBytes(0xffffffff))
      .isEqualTo(0
        + 1 /* tag of varint field */ + 5 // max size of varint32
      );

    assertThat(field.sizeInBytes(0L))
      .isZero();
    assertThat(field.sizeInBytes(0xffffffffffffffffL))
      .isEqualTo(0
        + 1 /* tag of varint field */ + 10 // max size of varint64
      );
  }

  @Test public void boolean_sizeInBytes() {
    BooleanField field = new BooleanField(1);

    assertThat(field.sizeInBytes(false))
      .isZero();
    assertThat(field.sizeInBytes(true))
      .isEqualTo(0
        + 1 /* tag of varint field */ + 1 // size of 1
      );
  }

  @Test public void utf8_sizeInBytes() {
    Utf8Field field = new Utf8Field(1);
    assertThat(field.sizeInBytes("12345678"))
      .isEqualTo(0
        + 1 /* tag of string field */ + 1 /* len */ + 8 // 12345678
      );
  }

  /** A map entry is an embedded messages: one for field the key and one for the value */
  @Test public void mapEntry_sizeInBytes() {
    MapEntryField field = new MapEntryField(1);
    assertThat(field.sizeInBytes(entry("123", "56789")))
      .isEqualTo(0
        + 1 /* tag of embedded key field */ + 1 /* len */ + 3
        + 1 /* tag of embedded value field  */ + 1 /* len */ + 5
        + 1 /* tag of map entry field */ + 1 /* len */
      );
  }

  @Test public void fixed64_sizeInBytes() {
    Fixed64Field field = new Fixed64Field(1);
    assertThat(field.sizeInBytes(Long.MIN_VALUE))
      .isEqualTo(9);
  }

  @Test public void fixed32_sizeInBytes() {
    Fixed32Field field = new Fixed32Field(1);
    assertThat(field.sizeInBytes(Integer.MIN_VALUE))
      .isEqualTo(5);
  }

  @Test public void supportedFields() {
    for (Field field : asList(
      new VarintField(128),
      new BooleanField(128),
      new HexField(128),
      new Utf8Field(128),
      new BytesField(128),
      new Fixed32Field(128),
      new Fixed64Field(128),
      new MapEntryField(128)
    )) {
      assertThat(Field.fieldNumber(field.key, 0))
        .isEqualTo(field.fieldNumber);
      assertThat(Field.wireType(field.key, 0))
        .isEqualTo(field.wireType);
    }
  }

  @Test public void fieldNumber_malformed() {
    try {
      Field.fieldNumber(0, 2);
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e)
        .hasMessage("fieldNumber was zero at position: 2");
    }
  }

  @Test public void wireType_unsupported() {
    for (int unsupported : asList(3, 4, 6)) {
      try {
        Field.wireType(1 << 3 | unsupported, 2);
        failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
      } catch (IllegalArgumentException e) {
        assertThat(e)
          .hasMessage("invalid wireType " + unsupported + " at position: 2");
      }
    }
  }

  @Test public void field_skipValue_VARINT() {
    VarintField field = new VarintField(128);
    field.write(buf, 0xffffffffffffffffL);

    buf.pos = 1; // skip the key
    skipValue(WIRETYPE_VARINT);
  }

  @Test public void field_skipValue_LENGTH_DELIMITED() {
    Utf8Field field = new Utf8Field(128);
    field.write(buf, "订单维护服务");

    buf.pos = 1; // skip the key
    skipValue(WIRETYPE_LENGTH_DELIMITED);
  }

  @Test public void field_skipValue_FIXED64() {
    Fixed64Field field = new Fixed64Field(128);
    field.write(buf, 0xffffffffffffffffL);

    buf.pos = 1; // skip the key
    skipValue(WIRETYPE_FIXED64);
  }

  @Test public void field_skipValue_FIXED32() {
    Fixed32Field field = new Fixed32Field(128);
    buf.writeByte(field.key);
    buf.writeByte(0xff).writeByte(0xff).writeByte(0xff).writeByte(0xff);

    buf.pos = 1; // skip the key
    skipValue(WIRETYPE_FIXED32);
  }

  @Test public void field_ensureLength_LENGTH_DELIMITED() {
    BytesField field = new BytesField(128);
    field.write(buf, new byte[10]);
    buf.pos = 1; // skip the key

    assertThat(field.ensureLength(buf))
      .isEqualTo(10);
  }

  @Test public void field_ensureLength_LENGTH_DELIMITED_truncated() {
    BytesField field = new BytesField(128);
    buf = new Buffer(10);
    buf.writeVarint(100); // much larger than the buffer size
    buf.pos = 0; // reset

    try {
      field.ensureLength(buf);
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("truncated: length 100 > bytes remaining 9");
    }
  }

  @Test public void field_read_FIXED64() {
    Fixed64Field field = new Fixed64Field(128);
    field.write(buf, 0xffffffffffffffffL);

    buf.pos = 1; // skip the key
    assertThat(field.readValue(buf))
      .isEqualTo(0xffffffffffffffffL);
  }

  void skipValue(int wireType) {
    assertThat(Field.skipValue(buf, wireType))
      .isTrue();
  }
}
