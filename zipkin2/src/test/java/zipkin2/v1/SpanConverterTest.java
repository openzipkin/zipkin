/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.v1;

import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;

public class SpanConverterTest {
  Endpoint kafka = Endpoint.newBuilder().serviceName("kafka").build();
  V2SpanConverter v2SpanConverter = new V2SpanConverter();
  V1SpanConverter v1SpanConverter = new V1SpanConverter();

  @Test
  public void client() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("get")
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .remoteEndpoint(BACKEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996238000L, "ws")
            .addAnnotation(1472470996403000L, "wr")
            .putTag("http.path", "/api")
            .putTag("clnt/finagle.version", "6.45.0")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996238000L, "ws", FRONTEND)
            .addAnnotation(1472470996403000L, "wr", FRONTEND)
            .addAnnotation(1472470996406000L, "cr", FRONTEND)
            .addBinaryAnnotation("http.path", "/api", FRONTEND)
            .addBinaryAnnotation("clnt/finagle.version", "6.45.0", FRONTEND)
            .addBinaryAnnotation("sa", BACKEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void client_unfinished() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("get")
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996238000L, "ws")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996238000L, "ws", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void client_kindInferredFromAnnotation() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("get")
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(1472470996238000L - 1472470996199000L)
            .addAnnotation(1472470996199000L, "cs")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(1472470996238000L - 1472470996199000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996238000L, "cr", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
  }

  @Test
  public void noAnnotationsExceptAddresses() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("get")
            .localEndpoint(FRONTEND)
            .remoteEndpoint(BACKEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addBinaryAnnotation("lc", "", FRONTEND)
            .addBinaryAnnotation("sa", BACKEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void redundantAddressAnnotations() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .kind(Kind.CLIENT)
            .name("get")
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996406000L, "cr", FRONTEND)
            .addBinaryAnnotation("ca", FRONTEND)
            .addBinaryAnnotation("sa", FRONTEND)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void server() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .name("get")
            .kind(Kind.SERVER)
            .localEndpoint(BACKEND)
            .remoteEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .putTag("http.path", "/api")
            .putTag("clnt/finagle.version", "6.45.0")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .id(2L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996199000L, "sr", BACKEND)
            .addAnnotation(1472470996406000L, "ss", BACKEND)
            .addBinaryAnnotation("http.path", "/api", BACKEND)
            .addBinaryAnnotation("clnt/finagle.version", "6.45.0", BACKEND)
            .addBinaryAnnotation("ca", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** Fix a v1 reported half in new style and half in old style, ex via a bridge */
  @Test
  public void client_missingCs() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .name("get")
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId("1")
            .id("2")
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996406000L, "cs", FRONTEND)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void server_missingSr() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .name("get")
            .kind(Kind.SERVER)
            .localEndpoint(BACKEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId("1")
            .id("2")
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996406000L, "ss", BACKEND)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** Buggy instrumentation can send data with missing endpoints. Make sure we can record it. */
  @Test
  public void missingEndpoints() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** No special treatment for invalid core annotations: missing endpoint */
  @Test
  public void missingEndpoints_coreAnnotation() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "sr")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "sr", null)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** Late flushed data on a v1 v1 */
  @Test
  public void lateRemoteEndpoint_ss() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .kind(Kind.SERVER)
            .localEndpoint(BACKEND)
            .remoteEndpoint(FRONTEND)
            .addAnnotation(1472470996199000L, "ss")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .addAnnotation(1472470996199000L, "ss", BACKEND)
            .addBinaryAnnotation("ca", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** Late flushed data on a v1 v1 */
  @Test
  public void lateRemoteEndpoint_ca() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .kind(Kind.SERVER)
            .remoteEndpoint(FRONTEND)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .addBinaryAnnotation("ca", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void lateRemoteEndpoint_cr() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .remoteEndpoint(BACKEND)
            .addAnnotation(1472470996199000L, "cr")
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .addAnnotation(1472470996199000L, "cr", FRONTEND)
            .addBinaryAnnotation("sa", BACKEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void lateRemoteEndpoint_sa() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("foo")
            .remoteEndpoint(BACKEND)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("foo")
            .addBinaryAnnotation("sa", BACKEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void localSpan_emptyComponent() {
    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("1")
            .id("2")
            .name("local")
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(1L)
            .id(2L)
            .name("local")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addBinaryAnnotation("lc", "", FRONTEND)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void clientAndServer() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996238000L, "ws", FRONTEND)
            .addAnnotation(1472470996250000L, "sr", BACKEND)
            .addAnnotation(1472470996350000L, "ss", BACKEND)
            .addAnnotation(1472470996403000L, "wr", FRONTEND)
            .addAnnotation(1472470996406000L, "cr", FRONTEND)
            .addBinaryAnnotation("http.path", "/api", FRONTEND)
            .addBinaryAnnotation("http.path", "/BACKEND", BACKEND)
            .addBinaryAnnotation("clnt/finagle.version", "6.45.0", FRONTEND)
            .addBinaryAnnotation("srv/finagle.version", "6.44.0", BACKEND)
            .addBinaryAnnotation("ca", FRONTEND)
            .addBinaryAnnotation("sa", BACKEND)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").parentId("2").id("3").name("get");

    // the v1 side owns timestamp and duration
    Span clientV2 =
        newBuilder
            .clone()
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .remoteEndpoint(BACKEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996238000L, "ws")
            .addAnnotation(1472470996403000L, "wr")
            .putTag("http.path", "/api")
            .putTag("clnt/finagle.version", "6.45.0")
            .build();

    // notice v1 tags are different than the v1, and the v1's annotations aren't here
    Span serverV2 =
        newBuilder
            .clone()
            .kind(Kind.SERVER)
            .shared(true)
            .localEndpoint(BACKEND)
            .remoteEndpoint(FRONTEND)
            .timestamp(1472470996250000L)
            .duration(100000L)
            .putTag("http.path", "/BACKEND")
            .putTag("srv/finagle.version", "6.44.0")
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(clientV2, serverV2);
  }

  /**
   * The old v1 format had no means of saying it is shared or not. This uses lack of timestamp as a
   * signal
   */
  @Test
  public void assumesServerWithoutTimestampIsShared() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .addAnnotation(1472470996250000L, "sr", BACKEND)
            .addAnnotation(1472470996350000L, "ss", BACKEND)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("get")
            .kind(Kind.SERVER)
            .shared(true)
            .localEndpoint(BACKEND)
            .timestamp(1472470996250000L)
            .duration(100000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void clientAndServer_loopback() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .timestamp(1472470996199000L)
            .duration(207000L)
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996250000L, "sr", FRONTEND)
            .addAnnotation(1472470996350000L, "ss", FRONTEND)
            .addAnnotation(1472470996406000L, "cr", FRONTEND)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").parentId("2").id("3").name("get");

    Span clientV2 =
        newBuilder
            .clone()
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(207000L)
            .build();

    Span serverV2 =
        newBuilder
            .clone()
            .kind(Kind.SERVER)
            .shared(true)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996250000L)
            .duration(100000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(clientV2, serverV2);
  }

  @Test
  public void oneway_loopback() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("get")
            .addAnnotation(1472470996199000L, "cs", FRONTEND)
            .addAnnotation(1472470996250000L, "sr", FRONTEND)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").parentId("2").id("3").name("get");

    Span clientV2 =
        newBuilder
            .clone()
            .kind(Kind.CLIENT)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .build();

    Span serverV2 =
        newBuilder
            .clone()
            .kind(Kind.SERVER)
            .shared(true)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996250000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(clientV2, serverV2);
  }

  @Test
  public void producer() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .addAnnotation(1472470996199000L, "ms", FRONTEND)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.PRODUCER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void producer_remote() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "ms", FRONTEND)
            .addBinaryAnnotation("ma", kafka)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.PRODUCER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .remoteEndpoint(kafka)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void producer_duration() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .timestamp(1472470996199000L)
            .duration(51000L)
            .addAnnotation(1472470996199000L, "ms", FRONTEND)
            .addAnnotation(1472470996250000L, "ws", FRONTEND)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.PRODUCER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(51000L)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void consumer() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "mr", FRONTEND)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.CONSUMER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void consumer_remote() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .timestamp(1472470996199000L)
            .addAnnotation(1472470996199000L, "mr", FRONTEND)
            .addBinaryAnnotation("ma", kafka)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.CONSUMER)
            .localEndpoint(FRONTEND)
            .remoteEndpoint(kafka)
            .timestamp(1472470996199000L)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test
  public void consumer_duration() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("send")
            .timestamp(1472470996199000L)
            .duration(51000L)
            .addAnnotation(1472470996199000L, "wr", FRONTEND)
            .addAnnotation(1472470996250000L, "mr", FRONTEND)
            .build();

    Span v2 =
        Span.newBuilder()
            .traceId("1")
            .parentId("2")
            .id("3")
            .name("send")
            .kind(Kind.CONSUMER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(51000L)
            .build();

    assertThat(v2SpanConverter.convert(v2)).isEqualToComparingFieldByFieldRecursively(v1);
    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  /** shared v1 IDs for messaging spans isn't supported, but shouldn't break */
  @Test
  public void producerAndConsumer() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1L)
            .parentId(2L)
            .id(3L)
            .name("whatev")
            .addAnnotation(1472470996199000L, "ms", FRONTEND)
            .addAnnotation(1472470996238000L, "ws", FRONTEND)
            .addAnnotation(1472470996403000L, "wr", BACKEND)
            .addAnnotation(1472470996406000L, "mr", BACKEND)
            .addBinaryAnnotation("ma", kafka)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").parentId("2").id("3").name("whatev");

    Span producer =
        newBuilder
            .clone()
            .kind(Kind.PRODUCER)
            .localEndpoint(FRONTEND)
            .remoteEndpoint(kafka)
            .timestamp(1472470996199000L)
            .duration(1472470996238000L - 1472470996199000L)
            .build();

    Span consumer =
        newBuilder
            .clone()
            .kind(Kind.CONSUMER)
            .shared(true)
            .localEndpoint(BACKEND)
            .remoteEndpoint(kafka)
            .timestamp(1472470996403000L)
            .duration(1472470996406000L - 1472470996403000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(producer, consumer);
  }

  /** shared v1 IDs for messaging spans isn't supported, but shouldn't break */
  @Test
  public void producerAndConsumer_loopback_shared() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1)
            .parentId(2)
            .id(3)
            .name("message")
            .addAnnotation(1472470996199000L, "ms", FRONTEND)
            .addAnnotation(1472470996238000L, "ws", FRONTEND)
            .addAnnotation(1472470996403000L, "wr", FRONTEND)
            .addAnnotation(1472470996406000L, "mr", FRONTEND)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").parentId("2").id("3").name("message");

    Span producer =
        newBuilder
            .clone()
            .kind(Kind.PRODUCER)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996199000L)
            .duration(1472470996238000L - 1472470996199000L)
            .build();

    Span consumer =
        newBuilder
            .clone()
            .kind(Kind.CONSUMER)
            .shared(true)
            .localEndpoint(FRONTEND)
            .timestamp(1472470996403000L)
            .duration(1472470996406000L - 1472470996403000L)
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(producer, consumer);
  }

  @Test
  public void dataMissingEndpointGoesOnFirstSpan() {
    V1Span v1 =
        V1Span.newBuilder()
            .traceId(1)
            .id(2)
            .name("missing")
            .addAnnotation(1472470996199000L, "foo", FRONTEND)
            .addAnnotation(1472470996238000L, "bar", FRONTEND)
            .addAnnotation(1472470996250000L, "baz", BACKEND)
            .addAnnotation(1472470996350000L, "qux", BACKEND)
            .addAnnotation(1472470996403000L, "missing", null)
            .addBinaryAnnotation("foo", "bar", FRONTEND)
            .addBinaryAnnotation("baz", "qux", BACKEND)
            .addBinaryAnnotation("missing", "", null)
            .build();

    Span.Builder newBuilder = Span.newBuilder().traceId("1").id("2").name("missing");

    Span first =
        newBuilder
            .clone()
            .localEndpoint(FRONTEND)
            .addAnnotation(1472470996199000L, "foo")
            .addAnnotation(1472470996238000L, "bar")
            .addAnnotation(1472470996403000L, "missing")
            .putTag("foo", "bar")
            .putTag("missing", "")
            .build();

    Span second =
        newBuilder
            .clone()
            .localEndpoint(BACKEND)
            .addAnnotation(1472470996250000L, "baz")
            .addAnnotation(1472470996350000L, "qux")
            .putTag("baz", "qux")
            .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(first, second);
  }
}
