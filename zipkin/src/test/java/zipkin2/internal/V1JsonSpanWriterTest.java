/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;

class V1JsonSpanWriterTest {
  V1JsonSpanWriter writer = new V1JsonSpanWriter();
  byte[] bytes = new byte[2048]; // bigger than needed to test sizeInBytes
  WriteBuffer buf = WriteBuffer.wrap(bytes);

  @Test void sizeInBytes() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(writer.sizeInBytes(CLIENT_SPAN)).isEqualTo(buf.pos());
  }

  @Test void writesCoreAnnotations_client() {
    writer.write(CLIENT_SPAN, buf);

    writesCoreAnnotations("cs", "cr");
  }

  @Test void writesCoreAnnotations_server() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesCoreAnnotations("sr", "ss");
  }

  @Test void writesCoreAnnotations_producer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreAnnotations("ms", "ws");
  }

  @Test void writesCoreAnnotations_consumer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.CONSUMER).build(), buf);

    writesCoreAnnotations("wr", "mr");
  }

  void writesCoreAnnotations(String begin, String end) {
    String json = new String(bytes, UTF_8);

    assertThat(json)
        .contains("{\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"value\":\"" + begin + "\"");
    assertThat(json)
        .contains("{\"timestamp\":"
          + (CLIENT_SPAN.timestampAsLong() + CLIENT_SPAN.durationAsLong())
          + ",\"value\":\"" + end + "\"");
  }

  @Test void writesCoreSendAnnotations_client() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).build(), buf);

    writesCoreSendAnnotations("cs");
  }

  @Test void writesCoreSendAnnotations_server() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.SERVER).build(), buf);

    writesCoreSendAnnotations("sr");
  }

  @Test void writesCoreSendAnnotations_producer() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreSendAnnotations("ms");
  }

  @Test void writesCoreSendAnnotations_consumer() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.CONSUMER).build(), buf);

    writesCoreSendAnnotations("mr");
  }

  void writesCoreSendAnnotations(String begin) {
    String json = new String(bytes, UTF_8);

    assertThat(json)
        .contains("{\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"value\":\"" + begin + "\"");
  }

  @Test void writesAddressBinaryAnnotation_client() {
    writer.write(CLIENT_SPAN.toBuilder().build(), buf);

    writesAddressBinaryAnnotation("sa");
  }

  @Test void writesAddressBinaryAnnotation_server() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesAddressBinaryAnnotation("ca");
  }

  @Test void writesAddressBinaryAnnotation_producer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  @Test void writesAddressBinaryAnnotation_consumer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.CONSUMER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  void writesAddressBinaryAnnotation(String address) {
    assertThat(new String(bytes, UTF_8))
      .contains("{\"key\":\"" + address + "\",\"value\":true,\"endpoint\":");
  }

  @Test void writes128BitTraceId() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .startsWith("{\"traceId\":\"" + CLIENT_SPAN.traceId() + "\"");
  }

  @Test void annotationsHaveEndpoints() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            "\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test void writesTimestampAndDuration() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            "\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"duration\":" + CLIENT_SPAN.duration());
  }

  @Test void skipsTimestampAndDuration_shared() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).shared(true).build(), buf);

    assertThat(new String(bytes, UTF_8))
        .doesNotContain(
            "\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"duration\":" + CLIENT_SPAN.duration());
  }

  @Test void writesEmptySpanName() {
    Span span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .parentId("6b221d5bc9e6496c")
            .id("5b4185666d50f68b")
            .build();

    writer.write(span, buf);

    assertThat(new String(bytes, UTF_8)).contains("\"name\":\"\"");
  }

  @Test void writesEmptyServiceName() {
    Span span =
        CLIENT_SPAN
            .toBuilder()
            .localEndpoint(Endpoint.newBuilder().ip("127.0.0.1").build())
            .build();

    writer.write(span, buf);

    assertThat(new String(bytes, UTF_8))
      .contains("\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test void tagsAreBinaryAnnotations() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            """
            "binaryAnnotations":[\
            {"key":"clnt/finagle.version","value":"6.45.0","endpoint":{"serviceName":"frontend","ipv4":"127.0.0.1"}},\
            {"key":"http.path","value":"/api","endpoint":{"serviceName":"frontend","ipv4":"127.0.0.1"}}\
            """);
  }
}
