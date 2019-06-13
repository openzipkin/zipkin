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
package zipkin2.codec;

import java.nio.charset.Charset;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.internal.Proto3SpanWriterTest;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;

/**
 * This test is intentionally sensitive to ensure our custom encoders do not break in subtle ways.
 *
 * <p>Test for {@link SpanBytesEncoder#PROTO3} are sanity-check only as the corresponding tests are
 * in {@link Proto3SpanWriterTest}.
 */
public class SpanBytesEncoderTest {
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  /**
   * Similar to {@link TestObjects#CLIENT_SPAN} except with fixed timestamps to ensure easy testing
   * of json literals.
   */
  public static final Span SPAN =
      Span.newBuilder()
          .traceId("7180c278b62e8f6a216a2aea45d08fc9")
          .parentId("6b221d5bc9e6496c")
          .id("5b4185666d50f68b")
          .name("get")
          .kind(Span.Kind.CLIENT)
          .localEndpoint(FRONTEND)
          .remoteEndpoint(BACKEND)
          .timestamp(1472470996199000L)
          .duration(207000L)
          .addAnnotation(1472470996238000L, "foo")
          .addAnnotation(1472470996403000L, "bar")
          .putTag("http.path", "/api")
          .putTag("clnt/finagle.version", "6.45.0")
          .build();

  // service name is surrounded by control characters
  public static final Span UTF8_SPAN =
      Span.newBuilder()
          .traceId("1")
          .id("1")
          // name is terrible
          .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
          // annotation value includes some json newline characters
          .addAnnotation(1L, "\u2028 and \u2029")
          // tag key includes a quote and value newlines
          .putTag(
              "\"foo", "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n")
          .build();

  public static final Span NO_ANNOTATIONS_ROOT_SERVER_SPAN =
      Span.newBuilder()
          .traceId("dc955a1d4768875d")
          .id("dc955a1d4768875d")
          .name("get")
          .timestamp(1510256710021866L)
          .duration(1117L)
          .kind(Span.Kind.SERVER)
          .localEndpoint(Endpoint.newBuilder().serviceName("isao01").ip("10.23.14.72").build())
          .putTag("http.path", "/rs/A")
          .putTag("location", "T67792")
          .putTag("other", "A")
          .build();

  public static final Span LOCAL_SPAN =
      Span.newBuilder()
          .traceId("dc955a1d4768875d")
          .id("dc955a1d4768875d")
          .name("encode")
          .timestamp(1510256710021866L)
          .duration(1117L)
          .localEndpoint(Endpoint.newBuilder().serviceName("isao01").ip("10.23.14.72").build())
          .build();

  /** the following skeletal span is used in dependency linking */
  public static final Span ERROR_SPAN =
    Span.newBuilder()
      .traceId("dc955a1d4768875d")
      .id("dc955a1d4768875d")
      .localEndpoint(Endpoint.newBuilder().serviceName("isao01").build())
      .kind(Span.Kind.CLIENT)
      .putTag("error", "")
      .build();

  Span span = SPAN;

  @Test
  public void span_JSON_V1() {
    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"annotations\":[{\"timestamp\":1472470996199000,\"value\":\"cs\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996238000,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996403000,\"value\":\"bar\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996406000,\"value\":\"cr\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}],\"binaryAnnotations\":[{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"sa\",\"value\":true,\"endpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000}}]}");
  }

  @Test
  public void span_JSON_V2() {
    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test
  public void span_PROTO3() {
    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(182);
  }

  @Test
  public void localSpan_JSON_V1() {
    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(LOCAL_SPAN), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"encode\",\"timestamp\":1510256710021866,\"duration\":1117,\"binaryAnnotations\":[{\"key\":\"lc\",\"value\":\"\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}]}");
  }

  @Test
  public void localSpan_JSON_V2() {
    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(LOCAL_SPAN), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"encode\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}");
  }

  @Test
  public void localSpan_PROTO3() {
    assertThat(SpanBytesEncoder.PROTO3.encode(LOCAL_SPAN)).hasSize(58);
  }

  @Test
  public void errorSpan_JSON_V2() {
    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(ERROR_SPAN), UTF_8))
      .isEqualTo(
        "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"CLIENT\",\"localEndpoint\":{\"serviceName\":\"isao01\"},\"tags\":{\"error\":\"\"}}");
  }

  @Test
  public void errorSpan_PROTO3() {
    assertThat(SpanBytesEncoder.PROTO3.encode(ERROR_SPAN)).hasSize(45);
  }

  @Test
  public void span_64bitTraceId_JSON_V1() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"annotations\":[{\"timestamp\":1472470996199000,\"value\":\"cs\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996238000,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996403000,\"value\":\"bar\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996406000,\"value\":\"cr\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}],\"binaryAnnotations\":[{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"sa\",\"value\":true,\"endpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000}}]}");
  }

  @Test
  public void span_64bitTraceId_JSON_V2() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test
  public void span_64bitTraceId_PROTO3() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(174);
  }

  @Test
  public void span_shared_JSON_V1() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"name\":\"get\",\"annotations\":[{\"timestamp\":1472470996199000,\"value\":\"sr\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996238000,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996403000,\"value\":\"bar\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996406000,\"value\":\"ss\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}],\"binaryAnnotations\":[{\"key\":\"ca\",\"value\":true,\"endpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000}},{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}]}");
  }

  @Test
  public void span_shared_JSON_V2() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"},\"shared\":true}");
  }

  @Test
  public void span_shared_PROTO3() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(184);
  }

  @Test
  public void specialCharsInJson_JSON_V1() {
    span = UTF8_SPAN;

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000001\",\"name\":\"\\\"\\\\\\t\\b\\n\\r\\f\",\"annotations\":[{\"timestamp\":1,\"value\":\"\\u2028 and \\u2029\"}],\"binaryAnnotations\":[{\"key\":\"\\\"foo\",\"value\":\"Database error: ORA-00942:\\u2028 and \\u2029 table or view does not exist\\n\"}]}");
  }

  @Test
  public void specialCharsInJson_JSON_V2() {
    span = UTF8_SPAN;

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000001\",\"name\":\"\\\"\\\\\\t\\b\\n\\r\\f\",\"annotations\":[{\"timestamp\":1,\"value\":\"\\u2028 and \\u2029\"}],\"tags\":{\"\\\"foo\":\"Database error: ORA-00942:\\u2028 and \\u2029 table or view does not exist\\n\"}}");
  }

  @Test
  public void specialCharsInJson_PROTO3() {
    span = UTF8_SPAN;

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(133);
  }

  @Test
  public void span_minimum_JSON_V1() {
    span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .id("5b4185666d50f68b")
            .build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"id\":\"5b4185666d50f68b\",\"name\":\"\"}");
  }

  @Test
  public void span_minimum_JSON_V2() {
    span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .id("5b4185666d50f68b")
            .build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"id\":\"5b4185666d50f68b\"}");
  }

  @Test
  public void span_minimum_PROTO3() {
    span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .id("5b4185666d50f68b")
            .build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(30);
  }

  @Test
  public void span_noLocalServiceName_JSON_V1() {
    span = span.toBuilder().localEndpoint(FRONTEND.toBuilder().serviceName(null).build()).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"annotations\":[{\"timestamp\":1472470996199000,\"value\":\"cs\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996238000,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996403000,\"value\":\"bar\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996406000,\"value\":\"cr\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}}],\"binaryAnnotations\":[{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"sa\",\"value\":true,\"endpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000}}]}");
  }

  @Test
  public void span_noLocalServiceName_JSON_V2() {
    span = span.toBuilder().localEndpoint(FRONTEND.toBuilder().serviceName(null).build()).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"serviceName\":\"backend\",\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test
  public void span_noLocalServiceName_PROTO3() {
    span = span.toBuilder().localEndpoint(FRONTEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(172);
  }

  @Test
  public void span_noRemoteServiceName_JSON_V1() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"annotations\":[{\"timestamp\":1472470996199000,\"value\":\"cs\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996238000,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996403000,\"value\":\"bar\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"timestamp\":1472470996406000,\"value\":\"cr\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}],\"binaryAnnotations\":[{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},{\"key\":\"sa\",\"value\":true,\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"192.168.99.101\",\"port\":9000}}]}");
  }

  @Test
  public void span_noRemoteServiceName_JSON_V2() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"7180c278b62e8f6a216a2aea45d08fc9\",\"parentId\":\"6b221d5bc9e6496c\",\"id\":\"5b4185666d50f68b\",\"kind\":\"CLIENT\",\"name\":\"get\",\"timestamp\":1472470996199000,\"duration\":207000,\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"},\"remoteEndpoint\":{\"ipv4\":\"192.168.99.101\",\"port\":9000},\"annotations\":[{\"timestamp\":1472470996238000,\"value\":\"foo\"},{\"timestamp\":1472470996403000,\"value\":\"bar\"}],\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}}");
  }

  @Test
  public void span_noRemoteServiceName_PROTO3() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(173);
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V1() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"get\",\"timestamp\":1510256710021866,\"duration\":1117,\"annotations\":[{\"timestamp\":1510256710021866,\"value\":\"sr\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"timestamp\":1510256710022983,\"value\":\"ss\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}],\"binaryAnnotations\":[{\"key\":\"http.path\",\"value\":\"/rs/A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"location\",\"value\":\"T67792\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"other\",\"value\":\"A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}]}");
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V2() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"}}");
  }

  @Test
  public void noAnnotations_rootServerSpan_PROTO3() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(109);
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V1_incomplete() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"get\",\"timestamp\":1510256710021866,\"annotations\":[{\"timestamp\":1510256710021866,\"value\":\"sr\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}],\"binaryAnnotations\":[{\"key\":\"http.path\",\"value\":\"/rs/A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"location\",\"value\":\"T67792\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"other\",\"value\":\"A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}]}");
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V2_incomplete() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"}}");
  }

  @Test
  public void noAnnotations_rootServerSpan_PROTO3_incomplete() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(106);
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V1_shared() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(new String(SpanBytesEncoder.JSON_V1.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"name\":\"get\",\"annotations\":[{\"timestamp\":1510256710021866,\"value\":\"sr\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"timestamp\":1510256710022983,\"value\":\"ss\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}],\"binaryAnnotations\":[{\"key\":\"http.path\",\"value\":\"/rs/A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"location\",\"value\":\"T67792\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}},{\"key\":\"other\",\"value\":\"A\",\"endpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"}}]}");
  }

  @Test
  public void noAnnotations_rootServerSpan_JSON_V2_shared() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8))
        .isEqualTo(
            "{\"traceId\":\"dc955a1d4768875d\",\"id\":\"dc955a1d4768875d\",\"kind\":\"SERVER\",\"name\":\"get\",\"timestamp\":1510256710021866,\"duration\":1117,\"localEndpoint\":{\"serviceName\":\"isao01\",\"ipv4\":\"10.23.14.72\"},\"tags\":{\"http.path\":\"/rs/A\",\"location\":\"T67792\",\"other\":\"A\"},\"shared\":true}");
  }

  @Test
  public void noAnnotations_rootServerSpan_PROTO3_shared() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(SpanBytesEncoder.PROTO3.encode(span)).hasSize(111);
  }

  @Test
  public void span_THRIFT() {
    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(503);
  }

  @Test
  public void localSpan_THRIFT() {
    assertThat(SpanBytesEncoder.THRIFT.encode(LOCAL_SPAN)).hasSize(127);
  }

  @Test
  public void span_64bitTraceId_THRIFT() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(492);
  }

  @Test
  public void span_shared_THRIFT() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(481);
  }

  @Test
  public void specialCharsInJson_THRIFT() {
    span = UTF8_SPAN;

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(176);
  }

  @Test
  public void span_minimum_THRIFT() {
    span =
        Span.newBuilder()
            .traceId("7180c278b62e8f6a216a2aea45d08fc9")
            .id("5b4185666d50f68b")
            .build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(57);
  }

  @Test
  public void span_noLocalServiceName_THRIFT() {
    span = span.toBuilder().localEndpoint(FRONTEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(455);
  }

  @Test
  public void span_noRemoteServiceName_THRIFT() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(496);
  }

  @Test
  public void noAnnotations_rootServerSpan_THRIFT() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(358);
  }

  @Test
  public void noAnnotations_rootServerSpan_THRIFT_incomplete() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(297);
  }

  @Test
  public void noAnnotations_rootServerSpan_THRIFT_shared() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(SpanBytesEncoder.THRIFT.encode(span)).hasSize(336);
  }
}
