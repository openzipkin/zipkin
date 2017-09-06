/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.CodecTest;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.internal.Util.UTF_8;

public final class JsonCodecTest extends CodecTest {

  @Override
  protected JsonCodec codec() {
    return Codec.JSON;
  }

  @Test
  public void tracesRoundTrip() throws IOException {
    List<List<Span>> traces = asList(TestObjects.TRACE, TestObjects.TRACE);
    byte[] bytes = codec().writeTraces(traces);
    assertThat(codec().readTraces(bytes))
        .isEqualTo(traces);
  }

  @Test
  public void stringsRoundTrip() throws IOException {
    List<String> strings = asList("foo", "bar", "baz");
    byte[] bytes = codec().writeStrings(strings);
    assertThat(codec().readStrings(bytes))
        .isEqualTo(strings);
  }

  @Test
  public void writesTraceIdHighIntoTraceIdField() {
    Span with128BitTraceId = Span.builder()
        .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124"))
        .traceId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
        .id(1).name("").build();

    assertThat(new String(Codec.JSON.writeSpan(with128BitTraceId), Util.UTF_8))
        .startsWith("{\"traceId\":\"48485a3953bb61246b221d5bc9e6496c\"");
  }

  @Test
  public void readsTraceIdHighFromTraceIdField() {
    byte[] with128BitTraceId = ("{\n"
        + "  \"traceId\": \"48485a3953bb61246b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}").getBytes(UTF_8);
    byte[] withLower64bitsTraceId = ("{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}").getBytes(UTF_8);

    assertThat(Codec.JSON.readSpan(with128BitTraceId))
        .isEqualTo(Codec.JSON.readSpan(withLower64bitsTraceId).toBuilder()
            .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124")).build());
  }

  @Test
  public void ignoreNull_parentId() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"parentId\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void ignoreNull_timestamp() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"timestamp\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void ignoreNull_duration() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"duration\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void ignoreNull_debug() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"debug\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void ignoreNull_annotation_endpoint() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"annotations\": [\n"
        + "    {\n"
        + "      \"timestamp\": 1461750491274000,\n"
        + "      \"value\": \"cs\",\n"
        + "      \"endpoint\": null\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void ignoreNull_binaryAnnotation_endpoint() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"lc\",\n"
        + "      \"value\": \"JDBCSpanStore\",\n"
        + "      \"endpoint\": null\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void niceErrorOnNull_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
        + "  \"traceId\": null,\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void niceErrorOnUppercaseTraceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
      "48485A3953BB6124 should be a 1 to 32 character lower-hex string with no prefix");

    String json = "{\n"
      + "  \"traceId\": \"48485A3953BB6124\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void niceErrorOnNull_id() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void binaryAnnotation_long() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"num\",\n"
        + "      \"value\": 123456789,\n"
        + "      \"type\": \"I64\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(UTF_8));
    assertThat(span.binaryAnnotations)
        .containsExactly(BinaryAnnotation.builder()
            .key("num")
            .type(BinaryAnnotation.Type.I64)
            .value(toBytes(123456789))
            .build());

    assertThat(Codec.JSON.readSpan(Codec.JSON.writeSpan(span)))
        .isEqualTo(span);
  }

  @Test
  public void binaryAnnotation_long_max() {
    String json = ("{"
        + "  \"traceId\": \"6b221d5bc9e6496c\","
        + "  \"id\": \"6b221d5bc9e6496c\","
        + "  \"name\": \"get-traces\","
        + "  \"binaryAnnotations\": ["
        + "    {"
        + "      \"key\": \"num\","
        + "      \"value\": \"9223372036854775807\","
        + "      \"type\": \"I64\""
        + "    }"
        + "  ]"
        + "}").replaceAll("\\s", "");

    Span span = Codec.JSON.readSpan(json.getBytes(UTF_8));
    assertThat(new String(Codec.JSON.writeSpan(span), UTF_8))
        .isEqualTo(json);
  }

  @Test
  public void binaryAnnotation_double() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"num\",\n"
        + "      \"value\": 1.23456789,\n"
        + "      \"type\": \"DOUBLE\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(UTF_8));
    assertThat(span.binaryAnnotations)
        .containsExactly(BinaryAnnotation.builder()
            .key("num")
            .type(BinaryAnnotation.Type.DOUBLE)
            .value(toBytes(Double.doubleToRawLongBits(1.23456789)))
            .build());

    assertThat(Codec.JSON.readSpan(Codec.JSON.writeSpan(span)))
        .isEqualTo(span);
  }

  @Test
  public void endpointHighPort() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"foo\",\n"
        + "      \"value\": \"bar\",\n"
        + "      \"endpoint\": {\n"
        + "        \"serviceName\": \"service\",\n"
        + "        \"port\": 65535\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(UTF_8));
    assertThat(span.binaryAnnotations)
        .containsExactly(BinaryAnnotation.create("foo", "bar",
            Endpoint.builder().serviceName("service").port(65535).build()));

    assertThat(Codec.JSON.readSpan(Codec.JSON.writeSpan(span)))
        .isEqualTo(span);
  }

  @Test
  public void mappedIPv6toIPv4() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"foo\",\n"
        + "      \"value\": \"bar\",\n"
        + "      \"endpoint\": {\n"
        + "        \"serviceName\": \"service\",\n"
        + "        \"port\": 65535,\n"
        + "        \"ipv6\": \"::ffff:192.0.2.128\"\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(UTF_8));
    assertThat(span.binaryAnnotations.get(0).endpoint.ipv6)
        .isNull();
    assertThat(span.binaryAnnotations.get(0).endpoint.ipv4)
        .isEqualTo((192 << 24) | (0 << 16) | (2 << 8) | 128); // 192.0.2.128
  }

  @Test
  public void missingKey() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("No key at $.binaryAnnotations[0]");

    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"value\": \"bar\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void missingValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("No value for key foo at $.binaryAnnotations[0]");

    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"foo\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(UTF_8));
  }

  @Test
  public void sizeInBytes_span() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0];
    assertThat(JsonCodec.SPAN_WRITER.sizeInBytes(span))
        .isEqualTo(codec().writeSpan(span).length);
  }

  @Test
  public void sizeInBytes_link() throws IOException {
    assertThat(JsonCodec.DEPENDENCY_LINK_WRITER.sizeInBytes(TestObjects.LINKS.get(0)))
        .isEqualTo(codec().writeDependencyLink(TestObjects.LINKS.get(0)).length);
  }

  static byte[] toBytes(long v) {
    okio.Buffer buffer = new okio.Buffer();
    buffer.writeLong(v);
    return buffer.readByteArray();
  }
}
