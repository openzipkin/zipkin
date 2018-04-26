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
import zipkin2.Span;
import zipkin2.internal.Proto3ZipkinFields.AnnotationField;
import zipkin2.internal.Proto3ZipkinFields.EndpointField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.internal.Proto3Fields.WIRETYPE_LENGTH_DELIMITED;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;

public class Proto3ZipkinFieldsTest {
  Buffer buf = new Buffer(2048); // bigger than needed to test sizeInBytes

  @Test public void annotation_sizeInBytes() {
    AnnotationField field = new AnnotationField(1 << 3 | WIRETYPE_LENGTH_DELIMITED);
    assertThat(field.sizeInBytes(Annotation.create(1L, "12345678")))
      .isEqualTo(0
        + 1 /* tag of timestamp field */ + 8 /* 8 byte number */
        + 1 /* tag of value field */ + 1 /* len */ + 8 // 12345678
        + 1 /* tag of annotation field */ + 1 /* len */
      );
  }

  @Test public void endpoint_sizeInBytes() {
    EndpointField field = new EndpointField(1 << 3 | WIRETYPE_LENGTH_DELIMITED);

    assertThat(field.sizeInBytes(Endpoint.newBuilder()
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

  @Test public void span_write_startsWithFieldInListOfSpans() {
    SPAN.write(buf, spanBuilder().build());

    assertThat(buf.toByteArray()).startsWith(
      0b00001010 /* span key */, 20 /* bytes for length of the span */
    );
  }

  @Test public void span_write_writesIds() {
    SPAN.write(buf, spanBuilder().build());
    assertThat(buf.toByteArray()).startsWith(
      0b00001010 /* span key */, 20 /* bytes for length of the span */,
      0b00001010 /* trace ID key */, 8 /* bytes for 64-bit trace ID */,
      0, 0, 0, 0, 0, 0, 0, 1, // hex trace ID
      0b00011010 /* span ID key */, 8 /* bytes for 64-bit span ID */,
      0, 0, 0, 0, 0, 0, 0, 2 // hex span ID
    );
    assertThat(buf.pos)
      .isEqualTo(3 * 2 /* overhead of three fields */ + 2 * 8 /* 64-bit fields */)
      .isEqualTo(22); // easier math on the next test
  }

  @Test public void span_read_ids() {
    assertRoundTrip(spanBuilder().parentId("1").build());
  }

  @Test public void span_read_name() {
    assertRoundTrip(spanBuilder().name("romeo").build());
  }

  @Test public void span_read_kind() {
    assertRoundTrip(spanBuilder().kind(Span.Kind.CONSUMER).build());
  }

  @Test public void span_read_timestamp_duration() {
    assertRoundTrip(spanBuilder().timestamp(TODAY).duration(134).build());
  }

  @Test public void span_read_endpoints() {
    assertRoundTrip(spanBuilder().localEndpoint(FRONTEND).remoteEndpoint(BACKEND).build());
  }

  @Test public void span_read_annotation() {
    assertRoundTrip(spanBuilder().addAnnotation(TODAY, "parked on sidewalk").build());
  }

  @Test public void span_read_tag() {
    assertRoundTrip(spanBuilder().putTag("foo", "bar").build());
  }

  @Test public void span_read_tag_empty() {
    assertRoundTrip(spanBuilder().putTag("empty", "").build());
  }

  @Test public void span_read_shared() {
    assertRoundTrip(spanBuilder().shared(true).build());
  }

  @Test public void span_read_debug() {
    assertRoundTrip(spanBuilder().debug(true).build());
  }

  @Test public void span_read() {
    assertRoundTrip(CLIENT_SPAN);
  }

  @Test public void span_write_omitsEmptyEndpoints() {
    SPAN.write(buf, spanBuilder()
      .localEndpoint(Endpoint.newBuilder().build())
      .remoteEndpoint(Endpoint.newBuilder().build())
      .build());

    assertThat(buf.pos)
      .isEqualTo(22);
  }

  @Test public void span_write_kind() {
    SPAN.write(buf, spanBuilder().kind(Span.Kind.PRODUCER).build());
    assertThat(buf.toByteArray())
      .contains(0b0100000, atIndex(22)); // (field_number << 3) | wire_type = 4 << 3 | 0
  }

  @Test public void span_write_debug() {
    SPAN.write(buf, CLIENT_SPAN.toBuilder().debug(true).build());

    assertThat(buf.toByteArray())
      .contains(0b01100000, atIndex(buf.pos - 2)) // (field_number << 3) | wire_type = 12 << 3 | 0
      .contains(1, atIndex(buf.pos - 1)); // true
  }

  @Test public void span_write_shared() {
    SPAN.write(buf, CLIENT_SPAN.toBuilder().shared(true).build());

    assertThat(buf.toByteArray())
      .contains(0b01101000, atIndex(buf.pos - 2)) // (field_number << 3) | wire_type = 13 << 3 | 0
      .contains(1, atIndex(buf.pos - 1)); // true
  }

  static Span.Builder spanBuilder() {
    return Span.newBuilder().traceId("1").id("2");
  }

  void assertRoundTrip(Span span) {
    SPAN.write(buf, span);
    buf.pos = 0; // reset

    assertThat(SPAN.read(buf))
      .isEqualTo(span);
  }
}
