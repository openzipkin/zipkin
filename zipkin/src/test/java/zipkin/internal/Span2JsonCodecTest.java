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
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.TraceKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.internal.Util.UTF_8;

public class Span2JsonCodecTest {
  Span2JsonCodec codec = new Span2JsonCodec();

  Endpoint frontend = Endpoint.create("frontend", 127 << 24 | 1);
  Endpoint backend = Endpoint.builder()
    .serviceName("backend")
    .ipv4(192 << 24 | 168 << 16 | 99 << 8 | 101)
    .port(9000)
    .build();

  Span2 span = Span2.builder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span2.Kind.CLIENT)
    .localEndpoint(frontend)
    .remoteEndpoint(backend)
    .timestamp(1472470996199000L)
    .duration(207000L)
    .addAnnotation(1472470996238000L, Constants.WIRE_SEND)
    .addAnnotation(1472470996403000L, Constants.WIRE_RECV)
    .putTag(TraceKeys.HTTP_PATH, "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void spanRoundTrip() throws IOException {
    byte[] bytes = codec.writeSpan(span);
    assertThat(codec.readSpan(bytes))
      .isEqualTo(span);
  }

  @Test public void sizeInBytes() throws IOException {
    assertThat(Span2JsonCodec.SPAN_WRITER.sizeInBytes(span))
      .isEqualTo(codec.writeSpan(span).length);
  }

  @Test public void spanRoundTrip_64bitTraceId() throws IOException {
    span = span.toBuilder().traceIdHigh(0L).build();
    byte[] bytes = codec.writeSpan(span);
    assertThat(codec.readSpan(bytes))
      .isEqualTo(span);
  }

  @Test public void spanRoundTrip_shared() throws IOException {
    span = span.toBuilder().shared(true).build();
    byte[] bytes = codec.writeSpan(span);
    assertThat(codec.readSpan(bytes))
      .isEqualTo(span);
  }

  @Test public void sizeInBytes_64bitTraceId() throws IOException {
    span = span.toBuilder().traceIdHigh(0L).build();
    assertThat(Span2JsonCodec.SPAN_WRITER.sizeInBytes(span))
      .isEqualTo(codec.writeSpan(span).length);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that trip-up
   * json don't fail in codec.
   */
  @Test public void specialCharsInJson() throws IOException {
    // service name is surrounded by control characters
    Span2 worstSpanInTheWorld = Span2.builder().traceId(1L).id(1L)
      // name is terrible
      .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
      .localEndpoint(Endpoint.create(new String(new char[] {0, 'a', 1}), 0))
      // annotation value includes some json newline characters
      .addAnnotation(1L, "\u2028 and \u2029")
      // tag key includes a quote and value newlines
      .putTag("\"foo", "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n")
      .build();

    byte[] bytes = codec.writeSpan(worstSpanInTheWorld);
    assertThat(codec.readSpan(bytes))
      .isEqualTo(worstSpanInTheWorld);
  }

  @Test public void niceErrorOnUppercaseTraceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
      "48485A3953BB6124 should be a 1 to 32 character lower-hex string with no prefix");

    String json = "{\n"
      + "  \"traceId\": \"48485A3953BB6124\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void decentErrorMessageOnEmptyInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading Span2");

    codec.readSpan(new byte[0]);
  }

  @Test public void decentErrorMessageOnEmptyInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<Span2>");

    codec.readSpans(new byte[0]);
  }

  @Test public void decentErrorMessageOnMalformedInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading Span2 from ");

    codec.readSpan(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test public void decentErrorMessageOnMalformedInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<Span2> from ");

    codec.readSpans(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  @Test public void spansRoundTrip() throws IOException {
    List<Span2> tenClientSpans = Collections.nCopies(10, span);

    byte[] bytes = codec.writeSpans(tenClientSpans);
    assertThat(codec.readSpans(bytes))
      .isEqualTo(tenClientSpans);
  }

  @Test public void writesTraceIdHighIntoTraceIdField() {
    Span2 with128BitTraceId = Span2.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124"))
      .traceId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .localEndpoint(frontend)
      .id(1).name("").build();

    assertThat(new String(codec.writeSpan(with128BitTraceId), Util.UTF_8))
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

    assertThat(codec.readSpan(with128BitTraceId))
      .isEqualTo(codec.readSpan(withLower64bitsTraceId).toBuilder()
        .traceIdHigh(Util.lowerHexToUnsignedLong("48485a3953bb6124")).build());
  }

  @Test public void ignoreNull_parentId() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"parentId\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_timestamp() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"timestamp\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_duration() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"duration\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_debug() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"debug\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_shared() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"shared\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_localEndpoint() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void ignoreNull_remoteEndpoint() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"remoteEndpoint\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": null,\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\"\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void niceErrorOnNull_id() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": null\n"
      + "}";

    codec.readSpan(json.getBytes(UTF_8));
  }

  @Test public void missingValue() {
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

    codec.readSpan(json.getBytes(UTF_8));
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

    assertThat(codec.readSpan(json.getBytes(UTF_8)).localEndpoint())
      .isEqualTo(Endpoint.create("", 127 << 24 | 1));
  }

  @Test public void readSpan_localEndpoint_nullServiceName() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"localEndpoint\": {\n"
      + "    \"serviceName\": null,\n"
      + "    \"ipv4\": \"127.0.0.1\"\n"
      + "  }\n"
      + "}";

    assertThat(codec.readSpan(json.getBytes(UTF_8)).localEndpoint())
      .isEqualTo(Endpoint.create("", 127 << 24 | 1));
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

    assertThat(codec.readSpan(json.getBytes(UTF_8)).remoteEndpoint())
      .isEqualTo(Endpoint.create("", 127 << 24 | 1));
  }

  @Test public void readSpan_remoteEndpoint_nullServiceName() {
    String json = "{\n"
      + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
      + "  \"name\": \"get-traces\",\n"
      + "  \"id\": \"6b221d5bc9e6496c\",\n"
      + "  \"remoteEndpoint\": {\n"
      + "    \"serviceName\": null,\n"
      + "    \"ipv4\": \"127.0.0.1\"\n"
      + "  }\n"
      + "}";

    assertThat(codec.readSpan(json.getBytes(UTF_8)).remoteEndpoint())
      .isEqualTo(Endpoint.create("", 127 << 24 | 1));
  }

  @Test public void spanRoundTrip_noRemoteServiceName() throws IOException {
    span = span.toBuilder().remoteEndpoint(backend.toBuilder().serviceName("").build()).build();
    byte[] bytes = codec.writeSpan(span);
    assertThat(codec.readSpan(bytes))
      .isEqualTo(span);
  }

  @Test public void doesntWriteEmptyServiceName() throws IOException {
    String expected = "{\"ipv4\":\"127.0.0.1\"}";
    Buffer b = new Buffer(expected.length());
    Span2JsonCodec.ENDPOINT_WRITER.write(Endpoint.create("", 127 << 24 | 1), b);
    assertThat(new String(b.toByteArray(), UTF_8))
      .isEqualTo(expected);
  }
}
