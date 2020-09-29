/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.internal.JsonCodec.UTF_8;

public class V1JsonSpanWriterTest {
  V1JsonSpanWriter writer = new V1JsonSpanWriter();
  byte[] bytes = new byte[2048]; // bigger than needed to test sizeInBytes
  WriteBuffer buf = WriteBuffer.wrap(bytes);

  @Test
  public void sizeInBytes() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(writer.sizeInBytes(CLIENT_SPAN)).isEqualTo(buf.pos());
  }

  @Test
  public void writesCoreAnnotations_client() {
    writer.write(CLIENT_SPAN, buf);

    writesCoreAnnotations("cs", "cr");
  }

  @Test
  public void writesCoreAnnotations_server() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesCoreAnnotations("sr", "ss");
  }

  @Test
  public void writesCoreAnnotations_producer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreAnnotations("ms", "ws");
  }

  @Test
  public void writesCoreAnnotations_consumer() {
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

  @Test
  public void writesCoreSendAnnotations_client() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).build(), buf);

    writesCoreSendAnnotations("cs");
  }

  @Test
  public void writesCoreSendAnnotations_server() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.SERVER).build(), buf);

    writesCoreSendAnnotations("sr");
  }

  @Test
  public void writesCoreSendAnnotations_producer() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreSendAnnotations("ms");
  }

  @Test
  public void writesCoreSendAnnotations_consumer() {
    writer.write(CLIENT_SPAN.toBuilder().duration(null).kind(Span.Kind.CONSUMER).build(), buf);

    writesCoreSendAnnotations("mr");
  }

  void writesCoreSendAnnotations(String begin) {
    String json = new String(bytes, UTF_8);

    assertThat(json)
        .contains("{\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"value\":\"" + begin + "\"");
  }

  @Test
  public void writesAddressBinaryAnnotation_client() {
    writer.write(CLIENT_SPAN.toBuilder().build(), buf);

    writesAddressBinaryAnnotation("sa");
  }

  @Test
  public void writesAddressBinaryAnnotation_server() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesAddressBinaryAnnotation("ca");
  }

  @Test
  public void writesAddressBinaryAnnotation_producer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  @Test
  public void writesAddressBinaryAnnotation_consumer() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.CONSUMER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  void writesAddressBinaryAnnotation(String address) {
    assertThat(new String(bytes, UTF_8))
      .contains("{\"key\":\"" + address + "\",\"value\":true,\"endpoint\":");
  }

  @Test
  public void writes128BitTraceId() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .startsWith("{\"traceId\":\"" + CLIENT_SPAN.traceId() + "\"");
  }

  @Test
  public void annotationsHaveEndpoints() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            "\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test
  public void writesTimestampAndDuration() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            "\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"duration\":" + CLIENT_SPAN.duration());
  }

  @Test
  public void skipsTimestampAndDuration_shared() {
    writer.write(CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).shared(true).build(), buf);

    assertThat(new String(bytes, UTF_8))
        .doesNotContain(
            "\"timestamp\":" + CLIENT_SPAN.timestamp() + ",\"duration\":" + CLIENT_SPAN.duration());
  }

  @Test
  public void writesEmptySpanName() {
    Span span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .parentId("6b221d5bc9e6496c")
            .id("5b4185666d50f68b")
            .build();

    writer.write(span, buf);

    assertThat(new String(bytes, UTF_8)).contains("\"name\":\"\"");
  }

  @Test
  public void writesEmptyServiceName() {
    Span span =
        CLIENT_SPAN
            .toBuilder()
            .localEndpoint(Endpoint.newBuilder().ip("127.0.0.1").build())
            .build();

    writer.write(span, buf);

    assertThat(new String(bytes, UTF_8))
        .contains("\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test
  public void tagsAreBinaryAnnotations() {
    writer.write(CLIENT_SPAN, buf);

    assertThat(new String(bytes, UTF_8))
        .contains(
            "\"binaryAnnotations\":["
                + "{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},"
                + "{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}");
  }
}
