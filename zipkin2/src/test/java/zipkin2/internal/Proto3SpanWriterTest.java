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
import zipkin2.Annotation;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto3SpanWriterTest {
  @Test public void sizeOfStringField() {
    assertThat(Proto3SpanWriter.sizeOfStringField("12345678"))
      .isEqualTo(0
        + 1 /* tag of string field */ + 1 /* len */ + 8 // 12345678
      );
  }

  @Test public void sizeOfAnnotationField_matchesProto3() {
    assertThat(Proto3SpanWriter.sizeOfAnnotationField(Annotation.create(1L, "12345678")))
      .isEqualTo(0
        + 1 /* tag of timestamp field */ + 8 /* 8 byte number */
        + 1 /* tag of value field */ + 1 /* len */ + 8 // 12345678
        + 1 /* tag of annotation field */ + 1 /* len */
      );
  }

  /** A map entry is an embedded messages: one for field the key and one for the value */
  @Test public void sizeOfMapEntryField() {
    assertThat(Proto3SpanWriter.sizeOfMapEntryField("123", "56789"))
      .isEqualTo(0
        + 1 /* tag of embedded key field */ + 1 /* len */ + 3
        + 1 /* tag of embedded value field  */ + 1 /* len */ + 5
        + 1 /* tag of map entry field */ + 1 /* len */
      );
  }

  @Test public void sizeOfEndpointField_matchesProto3() {
    assertThat(Proto3SpanWriter.sizeOfEndpointField(Endpoint.newBuilder()
      .serviceName("12345678")
      .ip("192.168.99.101")
      .ip("2001:db8::c001")
      .port(80)
      .build()))
      .isEqualTo(0
        + 1 /* tag of servicename field */ + 1 /* len */ + 8 // 12345678
        + 1 /* tag of ipv4 field */ + 1 /* len */ + 4 // octets in ipv4
        + 1 /* tag of ipv6 field */ + 1 /* len */ + 16 // octets in ipv6
        + 1 /* tag of port field */ + 1 /* small varint */
        + 1 /* tag of endpoint field */ + 1 /* len */
      );
  }
}
