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
package zipkin.internal.v2.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.TraceKeys;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class V2SpanWriterTest {
  V2SpanWriter writer = new V2SpanWriter();
  Buffer buf = new Buffer(2048); // bigger than needed to test sizeOf

  Endpoint frontend = Endpoint.newBuilder()
    .serviceName("frontend")
    .ip("127.0.0.1")
    .build();
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
    .putTag(TraceKeys.HTTP_PATH, "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void sizeInBytes() throws IOException {
    writer.write(span, buf);
    assertThat(writer.sizeInBytes(span))
      .isEqualTo(buf.pos);
  }

  @Test public void writes128BitTraceId() throws UnsupportedEncodingException {
    String traceId = "48485a3953bb61246b221d5bc9e6496c";
    span = Span.newBuilder().traceId(traceId).id("1").build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .startsWith("{\"traceId\":\"" + traceId + "\"");
  }

  @Test public void writesAnnotationWithoutEndpoint() throws IOException {
    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("{\"timestamp\":1472470996238000,\"value\":\"foo\"}");
  }

  @Test public void omitsEmptySpanName() throws IOException {
    span = Span.newBuilder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .doesNotContain("name");
  }

  @Test public void omitsEmptyServiceName() throws IOException {
    span = span.toBuilder()
      .localEndpoint(Endpoint.newBuilder().ip("127.0.0.1").build())
      .build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"localEndpoint\":{\"ipv4\":\"127.0.0.1\"}");
  }

  @Test public void tagsAreAMap() throws IOException {
    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"tags\":{\"clnt/finagle.version\":\"6.45.0\",\"http.path\":\"/api\"}");
  }
}
