/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
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

class JsonSerializersTest {
  @Test void span_ignoreNull_parentId() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "parentId": null
      }
      """;

    parse(SPAN_PARSER, json);
  }

  @Test void span_ignoreNull_timestamp() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "timestamp": null
      }
      """;

    parse(SPAN_PARSER, json);
  }

  @Test void span_ignoreNull_duration() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "duration": null
      }
      """;

    parse(SPAN_PARSER, json);
  }

  @Test void span_ignoreNull_debug() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "debug": null
      }
      """;

    parse(SPAN_PARSER, json);
  }

  @Test void span_ignoreNull_annotation_endpoint() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "annotations": [
          {
            "timestamp": 1461750491274000,
            "value": "cs",
            "endpoint": null
          }
        ]
      }
      """;

    parse(SPAN_PARSER, json);
  }

  @Test void span_tag_long_read() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "tags": {
            "num": 9223372036854775807
        }
      }
      """;

    Span span = parse(SPAN_PARSER, json);
    assertThat(span.tags()).containsExactly(entry("num", "9223372036854775807"));
  }

  @Test void span_tag_double_read() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "tags": {
            "num": 1.23456789
        }
      }
      """;

    Span span = parse(SPAN_PARSER, json);
    assertThat(span.tags()).containsExactly(entry("num", "1.23456789"));
  }

  @Test void span_roundTrip() {
    assertThat(parse(SPAN_PARSER, new String(SpanBytesEncoder.JSON_V2.encode(CLIENT_SPAN), UTF_8)))
      .isEqualTo(CLIENT_SPAN);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that trip-up
   * json don't fail in SPAN_PARSER.
   */
  @Test void span_specialCharsInJson() {
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

  @Test void span_endpointHighPort() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "localEndpoint": {
          "serviceName": "service",
          "port": 65535
        }
      }
      """;

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("service").port(65535).build());
  }

  @Test void span_noServiceName() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "localEndpoint": {
          "port": 65535
        }
      }
      """;

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("").port(65535).build());
  }

  @Test void span_nullServiceName() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "localEndpoint": {
          "serviceName": null,
          "port": 65535
        }
      }
      """;

    assertThat(parse(SPAN_PARSER, json).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("").port(65535).build());
  }

  @Test void span_readsTraceIdHighFromTraceIdField() throws IOException {
    String with128BitTraceId =
      ("""
        {
          "traceId": "48485a3953bb61246b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """);
    String withLower64bitsTraceId =
      ("""
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """);

    assertThat(parse(SPAN_PARSER, with128BitTraceId))
      .isEqualTo(
        parse(JsonSerializers.SPAN_PARSER, withLower64bitsTraceId)
          .toBuilder()
          .traceId("48485a3953bb61246b221d5bc9e6496c")
          .build());
  }

  @Test void dependencyLinkRoundTrip() {
    DependencyLink link =
      DependencyLink.newBuilder().parent("foo").child("bar").callCount(2).build();

    assertThat(parse(JsonSerializers.DEPENDENCY_LINK_PARSER,
      new String(DependencyLinkBytesEncoder.JSON_V1.encode(link), UTF_8))).isEqualTo(link);
  }

  @Test void dependencyLinkRoundTrip_withError() {
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
