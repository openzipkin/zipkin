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
package zipkin.internal.v2.codec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.TraceKeys;
import zipkin.internal.V2SpanConverter;
import zipkin.internal.v2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.V2SpanConverter.toEndpoint;

public class SpanJsonSpanAdaptersTest {
  Endpoint frontend = Endpoint.create("frontend", 127 << 24 | 1);
  Endpoint backend = Endpoint.builder()
    .serviceName("backend")
    .ipv4(192 << 24 | 168 << 16 | 99 << 8 | 101)
    .port(9000)
    .build();

  Span span = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span.Kind.CLIENT)
    .localEndpoint(toEndpoint(frontend))
    .remoteEndpoint(toEndpoint(backend))
    .timestamp(1472470996199000L)
    .duration(207000L)
    .addAnnotation(1472470996238000L, Constants.WIRE_SEND)
    .addAnnotation(1472470996403000L, Constants.WIRE_RECV)
    .putTag(TraceKeys.HTTP_PATH, "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void spanRoundTrip() throws IOException {
    assertThat(SpanBytesCodec.JSON.decode(SpanBytesEncoder.JSON.encode(span)))
      .isEqualTo(span);
  }

  @Test public void sizeInBytes() throws IOException {
    assertThat(SpanBytesEncoder.SPAN_WRITER.sizeInBytes(span))
      .isEqualTo(SpanBytesEncoder.JSON.encode(span).length);
  }

  @Test public void spanRoundTrip_64bitTraceId() throws IOException {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesCodec.JSON.decode(SpanBytesEncoder.JSON.encode(span)))
      .isEqualTo(span);
  }

  @Test public void spanRoundTrip_shared() throws IOException {
    span = span.toBuilder().shared(true).build();

    assertThat(SpanBytesCodec.JSON.decode(SpanBytesEncoder.JSON.encode(span)))
      .isEqualTo(span);
  }

  @Test public void sizeInBytes_64bitTraceId() throws IOException {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesEncoder.SPAN_WRITER.sizeInBytes(span))
      .isEqualTo(SpanBytesEncoder.JSON.encode(span).length);
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

    assertThat(SpanBytesCodec.JSON.decode(SpanBytesEncoder.JSON.encode(worstSpanInTheWorld)))
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnEmpty_inputSpans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<Span>");

    SpanBytesCodec.JSON.decodeList(new byte[0]);
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test public void niceErrorOnMalformed_inputSpans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<Span> from ");

    SpanBytesCodec.JSON.decodeList(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  @Test public void spansRoundTrip() throws IOException {
    List<Span> tenClientSpans = Collections.nCopies(10, span);

    byte[] message = SpanBytesEncoder.JSON.encodeList(tenClientSpans);

    assertThat(SpanBytesCodec.JSON.decodeList(message))
      .isEqualTo(tenClientSpans);
  }

  @Test public void writesTraceIdHighIntoTraceIdField() {
    Span with128BitTraceId = Span.newBuilder()
      .traceId("48485a3953bb61246b221d5bc9e6496c")
      .localEndpoint(toEndpoint(frontend))
      .id("1").name("").build();

    assertThat(new String(SpanBytesEncoder.JSON.encode(with128BitTraceId), UTF_8))
      .startsWith("{\"traceId\":\"48485a3953bb61246b221d5bc9e6496c\"");
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

    assertThat(SpanBytesCodec.JSON.decode(with128BitTraceId))
      .isEqualTo(SpanBytesCodec.JSON.decode(withLower64bitsTraceId).toBuilder()
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    assertThat(V2SpanConverter.toEndpoint(SpanBytesCodec.JSON.decode(json.getBytes(UTF_8)).localEndpoint()))
      .isEqualTo(Endpoint.create("", 127 << 24 | 1));
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
    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": null,\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_id() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": null\n"
      + "}";

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    SpanBytesCodec.JSON.decode(json.getBytes(UTF_8));
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

    assertThat(SpanBytesCodec.JSON.decode(json.getBytes(UTF_8)).localServiceName())
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

    assertThat(SpanBytesCodec.JSON.decode(json.getBytes(UTF_8)).remoteServiceName())
      .isNull();
  }

  @Test public void spanRoundTrip_noRemoteServiceName() throws IOException {
    span = span.toBuilder()
      .remoteEndpoint(toEndpoint(backend.toBuilder().serviceName("").build())).build();

    assertThat(SpanBytesCodec.JSON.decode(SpanBytesEncoder.JSON.encode(span)))
      .isEqualTo(span);
  }

  @Test public void doesntWriteEmptyServiceName() throws IOException {
    span = span.toBuilder()
      .localEndpoint(toEndpoint(frontend.toBuilder().serviceName("").build()))
      .remoteEndpoint(null).build();

    assertThat(new String(SpanBytesEncoder.JSON.encode(span), UTF_8))
      .contains("{\"ipv4\":\"127.0.0.1\"}");
  }
}
