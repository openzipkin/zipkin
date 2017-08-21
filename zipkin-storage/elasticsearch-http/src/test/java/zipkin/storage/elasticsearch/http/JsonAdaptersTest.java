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
import java.util.List;
import okio.Buffer;
import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;
import zipkin.internal.Util;
import zipkin.internal.v2.codec.Encoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin.storage.elasticsearch.http.JsonAdapters.SPAN_ADAPTER;

public class JsonAdaptersTest {
  @Test
  public void span_ignoreNull_parentId() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"parentId\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void span_ignoreNull_timestamp() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"timestamp\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void span_ignoreNull_duration() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"duration\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void span_ignoreNull_debug() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"debug\": null\n"
      + "}";

    SPAN_ADAPTER.fromJson(new Buffer().writeUtf8(json));
  }

  @Test
  public void span_ignoreNull_annotation_endpoint() throws IOException {
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
  public void span_tag_long_read() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"tags\": {"
      + "      \"num\": 9223372036854775807"
      + "  }"
      + "}";

    List<Span2> spans = Span2Converter.fromSpan(JsonAdapters.SPAN_ADAPTER.fromJson(json));
    assertThat(spans.get(0).tags())
      .containsExactly(entry("num", "9223372036854775807"));
  }

  @Test
  public void span_tag_double_read() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"tags\": {"
      + "      \"num\": 1.23456789"
      + "  }"
      + "}";

    List<Span2> spans = Span2Converter.fromSpan(JsonAdapters.SPAN_ADAPTER.fromJson(json));
    assertThat(spans.get(0).tags())
      .containsExactly(entry("num", "1.23456789"));
  }

  @Test
  public void span_roundTrip() throws IOException {
    Span span = ApplyTimestampAndDuration.apply(TestObjects.LOTS_OF_SPANS[0]);
    Span2 span2 = Span2Converter.fromSpan(span).get(0);
    Buffer bytes = new Buffer();
    bytes.write(Encoder.JSON.encode(span2));
    assertThat(SPAN_ADAPTER.fromJson(bytes))
      .isEqualTo(span);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that
   * trip-up json don't fail in SPAN_ADAPTER.
   */
  @Test
  public void span_specialCharsInJson() throws IOException {
    // service name is surrounded by control characters
    Endpoint e = Endpoint.create(new String(new char[] {0, 'a', 1}), 0);
    Span2 worstSpanInTheWorld = Span2.builder().traceId(1L).id(1L)
      // name is terrible
      .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
      .localEndpoint(e)
      // annotation value includes some json newline characters
      .addAnnotation(1L, "\u2028 and \u2029")
      // binary annotation key includes a quote and value newlines
      .putTag("\"foo",
        "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n")
      .build();

    Buffer bytes = new Buffer();
    bytes.write(Encoder.JSON.encode(worstSpanInTheWorld));
    assertThat(SPAN_ADAPTER.fromJson(bytes))
      .isEqualTo(Span2Converter.toSpan(worstSpanInTheWorld));
  }

  @Test
  public void span_endpointHighPort() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"serviceName\": \"service\",\n"
      + "    \"port\": 65535\n"
      + "  }\n"
      + "}";

    assertThat(SPAN_ADAPTER.fromJson(json).binaryAnnotations)
      .containsExactly(BinaryAnnotation.create("lc", "",
        Endpoint.builder().serviceName("service").port(65535).build()));
  }

  @Test
  public void span_noServiceName() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"port\": 65535\n"
      + "  }\n"
      + "}";

    assertThat(SPAN_ADAPTER.fromJson(json).binaryAnnotations)
      .containsExactly(BinaryAnnotation.create("lc", "",
        Endpoint.builder().serviceName("").port(65535).build()));
  }

  @Test
  public void span_nullServiceName() throws IOException {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"serviceName\": NULL,\n"
      + "    \"port\": 65535\n"
      + "  }\n"
      + "}";

    assertThat(SPAN_ADAPTER.fromJson(json).binaryAnnotations)
      .containsExactly(BinaryAnnotation.create("lc", "",
        Endpoint.builder().serviceName("").port(65535).build()));
  }

  @Test
  public void span_readsTraceIdHighFromTraceIdField() throws IOException {
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

    assertThat(JsonAdapters.SPAN_ADAPTER.fromJson(with128BitTraceId))
      .isEqualTo(JsonAdapters.SPAN_ADAPTER.fromJson(withLower64bitsTraceId).toBuilder()
        .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124")).build());
  }

  @Test
  public void dependencyLinkRoundTrip() throws IOException {
    DependencyLink link = DependencyLink.create("foo", "bar", 2);

    Buffer bytes = new Buffer();
    bytes.write(Codec.JSON.writeDependencyLink(link));
    assertThat(JsonAdapters.DEPENDENCY_LINK_ADAPTER.fromJson(bytes))
        .isEqualTo(link);
  }

  @Test
  public void dependencyLinkRoundTrip_withError() throws IOException {
    DependencyLink link = DependencyLink.builder()
      .parent("foo")
      .child("bar")
      .callCount(2)
      .errorCount(1).build();

    Buffer bytes = new Buffer();
    bytes.write(Codec.JSON.writeDependencyLink(link));
    assertThat(JsonAdapters.DEPENDENCY_LINK_ADAPTER.fromJson(bytes))
      .isEqualTo(link);
  }
}
