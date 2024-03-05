/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.Span.Kind.CLIENT;
import static zipkin2.Span.Kind.CONSUMER;
import static zipkin2.Span.Kind.PRODUCER;
import static zipkin2.Span.Kind.SERVER;
import static zipkin2.internal.ThriftField.TYPE_I16;
import static zipkin2.internal.ThriftField.TYPE_I32;
import static zipkin2.internal.ThriftField.TYPE_I64;
import static zipkin2.internal.ThriftField.TYPE_LIST;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.ThriftField.TYPE_STRUCT;

class V1ThriftSpanWriterTest {
  Span span = Span.newBuilder().traceId("1").id("2").build();
  Endpoint endpoint = Endpoint.newBuilder().serviceName("frontend").ip("1.2.3.4").build();
  byte[] bytes = new byte[2048]; // bigger than needed to test sizeOf
  WriteBuffer buf = WriteBuffer.wrap(bytes);

  V1ThriftSpanWriter writer = new V1ThriftSpanWriter();
  byte[] endpointBytes = new byte[ThriftEndpointCodec.sizeInBytes(endpoint)];

  @BeforeEach void init() {
    ThriftEndpointCodec.write(endpoint, WriteBuffer.wrap(endpointBytes, 0));
  }

  @Test void endpoint_highPort() {
    int highPort = 63840;
    Endpoint endpoint = Endpoint.newBuilder().ip("127.0.0.1").port(63840).build();
    byte[] buff = new byte[ThriftEndpointCodec.sizeInBytes(endpoint)];
    ThriftEndpointCodec.write(endpoint, WriteBuffer.wrap(buff, 0));

    assertThat(buff)
      .containsSequence(TYPE_I32, 0, 1, 127, 0, 0, 1) // ipv4
      .containsSequence(TYPE_I16, 0, 2, (highPort >> 8) & 0xFF, highPort & 0xFF); // port

    assertThat(ThriftEndpointCodec.read(ReadBuffer.wrap(buff)).portAsInt())
      .isEqualTo(highPort);
  }

  @Test void write_startsWithI64Prefix() {
    byte[] buff = writer.write(span);

    assertThat(buff)
      .hasSize(writer.sizeInBytes(span))
      .startsWith(TYPE_I64, 0, 1); // short value of field number 1
  }

  @Test void writeList_startsWithListPrefix() {
    byte[] buff = writer.writeList(List.of(span));

    assertThat(buff)
      .hasSize(5 + writer.sizeInBytes(span))
      .startsWith( // member type of the list and an integer with the count
        TYPE_STRUCT, 0, 0, 0, 1);
  }

  @Test void writeList_startsWithListPrefix_multiple() {
    byte[] buff = writer.writeList(List.of(span, span));

    assertThat(buff)
      .hasSize(5 + writer.sizeInBytes(span) * 2)
      .startsWith( // member type of the list and an integer with the count
        TYPE_STRUCT, 0, 0, 0, 2);
  }

  @Test void writeList_empty() {
    assertThat(writer.writeList(List.of())).isEmpty();
  }

  @Test void writeList_offset_startsWithListPrefix() {
    writer.writeList(List.of(span, span), bytes, 1);

    assertThat(bytes)
      .startsWith( // member type of the list and an integer with the count
        0, TYPE_STRUCT, 0, 0, 0, 2);
  }

  @Test void doesntWriteAnnotationsWhenMissingTimestamp() {
    writer.write(span.toBuilder().kind(CLIENT).build(), buf);

    byte[] bytes2 = new byte[2048];
    writer.write(span, WriteBuffer.wrap(bytes2));

    assertThat(bytes).containsExactly(bytes2);
  }

  @Test void writesCoreAnnotations_client_noEndpoint() {
    writesCoreAnnotationsNoEndpoint(CLIENT, "cs", "cr");
  }

  @Test void writesCoreAnnotations_server_noEndpoint() {
    writesCoreAnnotationsNoEndpoint(SERVER, "sr", "ss");
  }

  @Test void writesCoreAnnotations_producer_noEndpoint() {
    writesCoreAnnotationsNoEndpoint(PRODUCER, "ms", "ws");
  }

  @Test void writesCoreAnnotations_consumer_noEndpoint() {
    writesCoreAnnotationsNoEndpoint(CONSUMER, "wr", "mr");
  }

  void writesCoreAnnotationsNoEndpoint(Span.Kind kind, String begin, String end) {
    span = span.toBuilder().kind(kind).timestamp(5).duration(10).build();
    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 6, TYPE_STRUCT, 0, 0, 0, 2) // two annotations
      .containsSequence(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 5) // timestamp
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 2, begin.charAt(0), begin.charAt(1))
      .containsSequence(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 15) // timestamp
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 2, end.charAt(0), end.charAt(1));
  }

  @Test void writesBeginAnnotation_client_noEndpoint() {
    writesBeginAnnotationNoEndpoint(CLIENT, "cs");
  }

  @Test void writesBeginAnnotation_server_noEndpoint() {
    writesBeginAnnotationNoEndpoint(SERVER, "sr");
  }

  @Test void writesBeginAnnotation_producer_noEndpoint() {
    writesBeginAnnotationNoEndpoint(PRODUCER, "ms");
  }

  @Test void writesBeginAnnotation_consumer_noEndpoint() {
    writesBeginAnnotationNoEndpoint(CONSUMER, "mr");
  }

  void writesBeginAnnotationNoEndpoint(Span.Kind kind, String begin) {
    span = span.toBuilder().kind(kind).timestamp(5).build();
    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 6, TYPE_STRUCT, 0, 0, 0, 1) // one annotation
      .containsSequence(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 5) // timestamp
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 2, begin.charAt(0), begin.charAt(1));
  }

  @Test void writesAddressBinaryAnnotation_client() {
    writesAddressBinaryAnnotation(CLIENT, "sa");
  }

  @Test void writesAddressBinaryAnnotation_server() {
    writesAddressBinaryAnnotation(SERVER, "ca");
  }

  @Test void writesAddressBinaryAnnotation_producer() {
    writesAddressBinaryAnnotation(PRODUCER, "ma");
  }

  @Test void writesAddressBinaryAnnotation_consumer() {
    writesAddressBinaryAnnotation(CONSUMER, "ma");
  }

  void writesAddressBinaryAnnotation(Span.Kind kind, String addr) {
    writer.write(span.toBuilder().kind(kind).remoteEndpoint(endpoint).build(), buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 8, TYPE_STRUCT, 0, 0, 0, 1) // one binary annotation
      .containsSequence(TYPE_STRING, 0, 1, 0, 0, 0, 2, addr.charAt(0), addr.charAt(1)) // key
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 1, 1) // value
      .containsSequence(TYPE_I32, 0, 3, 0, 0, 0, 0) // type 0 == boolean
      .containsSequence(endpointBytes);
  }

  @Test void annotationsHaveEndpoints() {
    writer.write(span.toBuilder().localEndpoint(endpoint).addAnnotation(5, "foo").build(), buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 6, TYPE_STRUCT, 0, 0, 0, 1) // one annotation
      .containsSequence(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 5) // timestamp
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 3, 'f', 'o', 'o') // value
      .containsSequence(endpointBytes);
  }

  @Test void writesTimestampAndDuration() {
    writer.write(span.toBuilder().timestamp(5).duration(10).build(), buf);

    assertThat(bytes)
      .containsSequence(TYPE_I64, 0, 10, 0, 0, 0, 0, 0, 0, 0, 5) // timestamp
      .containsSequence(TYPE_I64, 0, 11, 0, 0, 0, 0, 0, 0, 0, 10); // duration
  }

  @Test void writesEmptySpanName() {
    Span span = Span.newBuilder().traceId("1").id("2").build();

    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(
        ThriftField.TYPE_STRING, 0, 3, 0, 0, 0, 0); // name (empty is 32 zero bits)
  }

  @Test void writesTraceAndSpanIds() {
    writer.write(span, buf);

    assertThat(bytes)
      .startsWith(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1) // trace ID
      .containsSequence(TYPE_I64, 0, 4, 0, 0, 0, 0, 0, 0, 0, 2); // ID
  }

  @Test void writesParentAnd128BitTraceId() {
    writer.write(
      Span.newBuilder().traceId("00000000000000010000000000000002").parentId("3").id("4").build(),
      buf);

    assertThat(bytes)
      .startsWith(TYPE_I64, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2) // trace ID
      .containsSequence(TYPE_I64, 0, 12, 0, 0, 0, 0, 0, 0, 0, 1) // trace ID high
      .containsSequence(TYPE_I64, 0, 5, 0, 0, 0, 0, 0, 0, 0, 3); // parent ID
  }

  /**
   * For finagle compatibility
   */
  @Test void writesEmptyAnnotationAndBinaryAnnotations() {
    Span span = Span.newBuilder().traceId("1").id("2").build();

    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 6, TYPE_STRUCT, 0, 0, 0, 0) // empty annotations
      .containsSequence(TYPE_LIST, 0, 8, TYPE_STRUCT, 0, 0, 0, 0); // empty binary annotations
  }

  @Test void writesEmptyLocalComponentWhenNoAnnotationsOrTags() {
    span = span.toBuilder().name("foo").localEndpoint(endpoint).build();

    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 8, TYPE_STRUCT, 0, 0, 0, 1) // one binary annotation
      .containsSequence(TYPE_STRING, 0, 1, 0, 0, 0, 2, 'l', 'c') // key
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 0) // empty value
      .containsSequence(TYPE_I32, 0, 3, 0, 0, 0, 6) // type 6 == string
      .containsSequence(endpointBytes);
  }

  @Test void writesEmptyServiceName() {
    span =
      span.toBuilder()
        .name("foo")
        .localEndpoint(Endpoint.newBuilder().ip("127.0.0.1").build())
        .build();

    writer.write(span, buf);

    assertThat(bytes)
      .containsSequence(
        ThriftField.TYPE_STRING, 0, 3, 0, 0, 0, 0); // serviceName (empty is 32 zero bits)
  }

  /** To match finagle */
  @Test void writesDebugFalse() {
    span = span.toBuilder().debug(false).build();

    writer.write(span, buf);

    assertThat(bytes).containsSequence(ThriftField.TYPE_BOOL, 0);
  }

  @Test void tagsAreBinaryAnnotations() {
    writer.write(span.toBuilder().putTag("foo", "bar").build(), buf);

    assertThat(bytes)
      .containsSequence(TYPE_LIST, 0, 8, TYPE_STRUCT, 0, 0, 0, 1) // one binary annotation
      .containsSequence(TYPE_STRING, 0, 1, 0, 0, 0, 3, 'f', 'o', 'o') // key
      .containsSequence(TYPE_STRING, 0, 2, 0, 0, 0, 3, 'b', 'a', 'r') // value
      .containsSequence(TYPE_I32, 0, 3, 0, 0, 0, 6); // type 6 == string
  }
}
