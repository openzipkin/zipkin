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
package zipkin2.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.UTF_8;

public class SpanBytesEncoderTest {
  Endpoint frontend = Endpoint.newBuilder()
    .serviceName("frontend")
    .ip("127.0.0.1").build();
  Endpoint backend = Endpoint.newBuilder()
    .serviceName("backend")
    .ip("192.168.99.101")
    .port(9000)
    .build();

  Span span = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span.Kind.CLIENT)
    .localEndpoint(frontend)
    .remoteEndpoint(backend)
    .timestamp(1472470996199000L)
    .duration(207000L)
    .addAnnotation(1472470996238000L, "foo")
    .addAnnotation(1472470996403000L, "bar")
    .putTag("http.path", "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void spanRoundTrip() throws IOException {
    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(span)))
      .isEqualTo(span);
  }

  @Test public void spanRoundTrip_64bitTraceId() throws IOException {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(span)))
      .isEqualTo(span);
  }

  @Test public void spanRoundTrip_shared() throws IOException {
    span = span.toBuilder().shared(true).build();

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(span)))
      .isEqualTo(span);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that trip-up
   * json don't fail in codec.
   */
  @Test public void specialCharsInJson() throws IOException {
    // service name is surrounded by control characters
    Span worstSpanInTheWorld = Span.newBuilder().traceId("1").id("1")
      // name is terrible
      .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
      // annotation value includes some json newline characters
      .addAnnotation(1L, "\u2028 and \u2029")
      // tag key includes a quote and value newlines
      .putTag("\"foo", "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n")
      .build();

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(worstSpanInTheWorld)))
      .isEqualTo(worstSpanInTheWorld);
  }

  @Test public void niceErrorOnUppercase_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("48485A3953BB6124 should be lower-hex encoded with no prefix");

    String json = "{\n"
      + "  \"traceId\": \"48485A3953BB6124\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void falseOnEmpty_inputSpans() throws IOException {
    assertThat(SpanBytesDecoder.JSON_V2.decodeList(new byte[0], new ArrayList<>()))
      .isFalse();
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test public void niceErrorOnMalformed_inputSpans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<Span> from ");

    SpanBytesDecoder.JSON_V2.decodeList(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  @Test public void spansRoundTrip() throws IOException {
    List<Span> tenClientSpans = Collections.nCopies(10, span);

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(tenClientSpans);

    assertThat(SpanBytesDecoder.JSON_V2.decodeList(message))
      .isEqualTo(tenClientSpans);
  }

  @Test public void readsTraceIdHighFromTraceIdField() {
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

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(with128BitTraceId))
      .isEqualTo(SpanBytesDecoder.JSON_V2.decodeOne(withLower64bitsTraceId).toBuilder()
        .traceId("48485a3953bb61246b221d5bc9e6496c").build());
  }

  @Test public void ignoresNull_topLevelFields() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"parentId\": null,\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": null,\n"
      + "  \"timestamp\": null,\n"
      + "  \"duration\": null,\n"
      + "  \"localEndpoint\": null,\n"
      + "  \"remoteEndpoint\": null,\n"
      + "  \"annotations\": null,\n"
      + "  \"tags\": null,\n"
      + "  \"debug\": null,\n"
      + "  \"shared\": null\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void ignoresNull_endpoint_topLevelFields() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"serviceName\": null,\n"
      + "    \"ipv4\": \"127.0.0.1\",\n"
      + "    \"ipv6\": null,\n"
      + "    \"port\": null\n"
      + "  }\n"
      + "}";

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8)).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().ip("127.0.0.1").build());
  }

  @Test public void niceErrorOnIncomplete_endpoint() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty endpoint at $.localEndpoint reading Span from json");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"serviceName\": null,\n"
      + "    \"ipv4\": null,\n"
      + "    \"ipv6\": null,\n"
      + "    \"port\": null\n"
      + "  }\n"
      + "}";
    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnIncomplete_annotation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Incomplete annotation at $.annotations[0].timestamp");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"annotations\": [\n"
      + "    { \"timestamp\": 1472470996199000}\n"
      + "  ]\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": null,\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_id() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": null\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_tagValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("No value at $.tags.foo");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"tags\": {\n"
      + "    \"foo\": NULL\n"
      + "  }\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_annotationValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("$.annotations[0].value");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"annotations\": [\n"
      + "    { \"timestamp\": 1472470996199000, \"value\": NULL}\n"
      + "  ]\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_annotationTimestamp() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("$.annotations[0].timestamp");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"annotations\": [\n"
      + "    { \"timestamp\": NULL, \"value\": \"foo\"}\n"
      + "  ]\n"
      + "}";

    SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8));
  }

  @Test public void readSpan_localEndpoint_noServiceName() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"ipv4\": \"127.0.0.1\"\n"
      + "  }\n"
      + "}";

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8)).localServiceName())
      .isNull();
  }

  @Test public void readSpan_remoteEndpoint_noServiceName() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"remoteEndpoint\": {\n"
      + "    \"ipv4\": \"127.0.0.1\"\n"
      + "  }\n"
      + "}";

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(json.getBytes(UTF_8)).remoteServiceName())
      .isNull();
  }

  @Test public void spanRoundTrip_noRemoteServiceName() throws IOException {
    span = span.toBuilder()
      .remoteEndpoint(backend.toBuilder().serviceName(null).build())
      .build();

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(span)))
      .isEqualTo(span);
  }
}
