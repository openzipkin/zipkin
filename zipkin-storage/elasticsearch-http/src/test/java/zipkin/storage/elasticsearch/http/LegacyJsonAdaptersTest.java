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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import okio.Buffer;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.Util;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.storage.elasticsearch.http.LegacyJsonAdapters.SPAN_ADAPTER;

public class LegacyJsonAdaptersTest {
  @Test
  public void ignoreNull_parentId() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"parentId\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void ignoreNull_timestamp() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"timestamp\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void ignoreNull_duration() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"duration\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void ignoreNull_debug() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"debug\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void ignoreNull_annotation_endpoint() throws IOException {
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

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void ignoreNull_binaryAnnotation_endpoint() throws IOException {
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

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void binaryAnnotation_long_read() throws IOException {
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

    Span span = SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
    assertThat(span.binaryAnnotations)
      .containsExactly(BinaryAnnotation.builder()
        .key("num")
        .type(BinaryAnnotation.Type.I64)
        .value(new Buffer().writeLong(123456789).readByteArray())
        .build());
  }

  @Test
  public void binaryAnnotation_double_read() throws IOException {
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

    Span span = SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
    assertThat(span.binaryAnnotations)
      .containsExactly(BinaryAnnotation.builder()
        .key("num")
        .type(BinaryAnnotation.Type.DOUBLE)
        .value(new Buffer().writeLong(Double.doubleToRawLongBits(1.23456789)).readByteArray())
        .build());
  }

  @Test
  public void spanRoundTrip() throws IOException {
    for (Span span : TestObjects.TRACE) {
      Buffer bytes = new Buffer();
      bytes.write(Codec.JSON.writeSpan(span));
      assertThat(SPAN_ADAPTER.fromJson(bytes))
        .isEqualTo(span);
    }
  }

  @Test
  public void binaryAnnotation_long() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0].toBuilder().binaryAnnotations(asList(
      BinaryAnnotation.builder()
        .key("Long.zero")
        .type(BinaryAnnotation.Type.I64)
        .value(ByteBuffer.allocate(8).putLong(0, 0L).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Long.negative")
        .type(BinaryAnnotation.Type.I64)
        .value(ByteBuffer.allocate(8).putLong(0, -1005656679588439279L).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Long.MIN_VALUE")
        .type(BinaryAnnotation.Type.I64)
        .value(ByteBuffer.allocate(8).putLong(0, Long.MIN_VALUE).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Long.MAX_VALUE")
        .type(BinaryAnnotation.Type.I64)
        .value(ByteBuffer.allocate(8).putLong(0, Long.MAX_VALUE).array())
        .build()
    )).build();

    Buffer bytes = new Buffer();
    bytes.write(Codec.JSON.writeSpan(span));
    assertThat(SPAN_ADAPTER.fromJson(bytes))
      .isEqualTo(span);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that
   * trip-up json don't fail in SPAN_ADAPTER.
   */
  @Test
  public void specialCharsInJson() throws IOException {
    // service name is surrounded by control characters
    Endpoint e = Endpoint.create(new String(new char[] {0, 'a', 1}), 0);
    Span worstSpanInTheWorld = Span.builder().traceId(1L).id(1L)
      // name is terrible
      .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
      // annotation value includes some json newline characters
      .addAnnotation(Annotation.create(1L, "\u2028 and \u2029", e))
      // binary annotation key includes a quote and value newlines
      .addBinaryAnnotation(BinaryAnnotation.create("\"foo",
        "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n", e))
      .build();

    Buffer bytes = new Buffer();
    bytes.write(Codec.JSON.writeSpan(worstSpanInTheWorld));
    assertThat(SPAN_ADAPTER.fromJson(bytes))
      .isEqualTo(worstSpanInTheWorld);
  }

  @Test
  public void binaryAnnotation_double() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0].toBuilder().binaryAnnotations(asList(
      BinaryAnnotation.builder()
        .key("Double.zero")
        .type(BinaryAnnotation.Type.DOUBLE)
        .value(ByteBuffer.allocate(8).putDouble(0, 0.0).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Double.negative")
        .type(BinaryAnnotation.Type.DOUBLE)
        .value(ByteBuffer.allocate(8).putDouble(0, -1.005656679588439279).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Double.MIN_VALUE")
        .type(BinaryAnnotation.Type.DOUBLE)
        .value(ByteBuffer.allocate(8).putDouble(0, Double.MIN_VALUE).array())
        .build(),
      BinaryAnnotation.builder()
        .key("Double.MAX_VALUE")
        .type(BinaryAnnotation.Type.I64)
        .value(ByteBuffer.allocate(8).putDouble(0, Double.MAX_VALUE).array())
        .build()
    )).build();

    Buffer bytes = new Buffer();
    bytes.write(Codec.JSON.writeSpan(span));
    assertThat(SPAN_ADAPTER.fromJson(bytes))
      .isEqualTo(span);
  }

  @Test
  public void endpointHighPort() throws IOException {
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

    assertThat(SPAN_ADAPTER.fromJson(json).binaryAnnotations)
      .containsExactly(BinaryAnnotation.create("foo", "bar",
        Endpoint.builder().serviceName("service").port(65535).build()));
  }

  @Test
  public void readsTraceIdHighFromTraceIdField() throws IOException {
    String with128BitTraceId = ("{\n"
      + "  \"traceId\": \"48485a3953bb61246b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}");
    String withLower64bitsTraceId = ("{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}");

    assertThat(SPAN_ADAPTER.fromJson(with128BitTraceId))
      .isEqualTo(SPAN_ADAPTER.fromJson(withLower64bitsTraceId).toBuilder()
        .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124")).build());
  }

  @Test
  public void binaryAnnotation_long_max() throws IOException {
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

    Span span = SPAN_ADAPTER.fromJson(json);
    assertThat(span.binaryAnnotations).extracting(b -> ByteBuffer.wrap(b.value).getLong())
      .containsExactly(9223372036854775807L);
  }
}
