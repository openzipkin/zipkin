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
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.Test;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.elasticsearch.internal.JsonSerializers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.UTF_8;
import static zipkin2.elasticsearch.internal.JsonSerializers.SPAN_PARSER;

public class JsonSerializersTest {
  @Test
  public void span_ignoreNull_parentId() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"parentId\": null\n"
        + "}";

    parse(SPAN_PARSER, json);
  }

  @Test
  public void span_ignoreNull_timestamp() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"timestamp\": null\n"
        + "}";

    parse(SPAN_PARSER, json);
  }

  @Test
  public void span_ignoreNull_duration() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"duration\": null\n"
        + "}";

    parse(SPAN_PARSER, json);
  }

  @Test
  public void span_ignoreNull_debug() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"debug\": null\n"
        + "}";

    parse(SPAN_PARSER, json);
  }

  @Test
  public void span_ignoreNull_annotation_endpoint() {
    String json =
      "{\n"
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

    parse(SPAN_PARSER, json);
  }

  @Test
  public void span_tag_long_read() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"tags\": {"
        + "      \"num\": 9223372036854775807"
        + "  }"
        + "}";

    Span span = parse(SPAN_PARSER, json);
    assertThat(span.tags()).containsExactly(entry("num", "9223372036854775807"));
  }

  @Test
  public void span_tag_double_read() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"tags\": {"
        + "      \"num\": 1.23456789"
        + "  }"
        + "}";

    Span span = parse(SPAN_PARSER, json);
    assertThat(span.tags()).containsExactly(entry("num", "1.23456789"));
  }

  @Test
  public void span_roundTrip() {
    assertThat(parse(SPAN_PARSER, new String(SpanBytesEncoder.JSON_V2.encode(CLIENT_SPAN), UTF_8)))
      .isEqualTo(CLIENT_SPAN);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that trip-up
   * json don't fail in SPAN_PARSER.
   */
  @Test
  public void span_specialCharsInJson() {
    // service name is surrounded by control characters
    Endpoint e = Endpoint.newBuilder().serviceName(new String(new char[] {0, 'a', 1})).build();
    Span worstSpanInTheWorld =
      Span.newBuilder()
        .traceId("1")
        .id("1")
        // name is terrible
        .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
        .localEndpoint(e)
        // annotation value includes some json newline characters
        .addAnnotation(1L, "\u2028 and \u2029")
        // binary annotation key includes a quote and value newlines
        .putTag(
          "\"foo",
          "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n")
        .build();

    assertThat(
      parse(SPAN_PARSER, new String(SpanBytesEncoder.JSON_V2.encode(worstSpanInTheWorld), UTF_8)))
      .isEqualTo(worstSpanInTheWorld);
  }

  @Test
  public void span_endpointHighPort() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"localEndpoint\": {\n"
        + "    \"serviceName\": \"service\",\n"
        + "    \"port\": 65535\n"
        + "  }\n"
        + "}";

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("service").port(65535).build());
  }

  @Test
  public void span_noServiceName() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"localEndpoint\": {\n"
        + "    \"port\": 65535\n"
        + "  }\n"
        + "}";

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("").port(65535).build());
  }

  @Test
  public void span_nullServiceName() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"localEndpoint\": {\n"
        + "    \"serviceName\": null,\n"
        + "    \"port\": 65535\n"
        + "  }\n"
        + "}";

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("").port(65535).build());
  }

  @Test
  public void span_readsTraceIdHighFromTraceIdField() throws IOException {
    String with128BitTraceId =
      ("{\n"
        + "  \"traceId\": \"48485a3953bb61246b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}");
    String withLower64bitsTraceId =
      ("{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}");

    assertThat(parse(SPAN_PARSER, with128BitTraceId))
      .isEqualTo(
        parse(JsonSerializers.SPAN_PARSER, withLower64bitsTraceId)
          .toBuilder()
          .traceId("48485a3953bb61246b221d5bc9e6496c")
          .build());
  }

  @Test
  public void dependencyLinkRoundTrip() {
    DependencyLink link =
      DependencyLink.newBuilder().parent("foo").child("bar").callCount(2).build();

    assertThat(parse(JsonSerializers.DEPENDENCY_LINK_PARSER,
      new String(DependencyLinkBytesEncoder.JSON_V1.encode(link), UTF_8))).isEqualTo(link);
  }

  @Test
  public void dependencyLinkRoundTrip_withError() {
    DependencyLink link =
      DependencyLink.newBuilder().parent("foo").child("bar").callCount(2).errorCount(1).build();

    assertThat(parse(JsonSerializers.DEPENDENCY_LINK_PARSER,
      new String(DependencyLinkBytesEncoder.JSON_V1.encode(link), UTF_8))).isEqualTo(link);
  }

  static <T> T parse(JsonSerializers.ObjectParser<T> parser, String json) {
    try {
      JsonParser jsonParser = JsonSerializers.JSON_FACTORY.createParser(json);
      jsonParser.nextToken();
      return parser.parse(jsonParser);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
