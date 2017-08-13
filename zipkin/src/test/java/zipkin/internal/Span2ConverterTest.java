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

import java.nio.ByteBuffer;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TraceKeys;
import zipkin.internal.Span2.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.LOCAL_COMPONENT;

public class Span2ConverterTest {
  Endpoint frontend = Endpoint.create("frontend", 127 << 24 | 1);
  Endpoint backend = Endpoint.builder()
    .serviceName("backend")
    .ipv4(192 << 24 | 168 << 16 | 99 << 8 | 101)
    .port(9000)
    .build();
  Endpoint kafka = Endpoint.create("kafka", 0);

  @Test public void client() {
    Span2 simpleClient = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .kind(Kind.CLIENT)
      .localEndpoint(frontend)
      .remoteEndpoint(backend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(1472470996238000L, Constants.WIRE_SEND)
      .addAnnotation(1472470996403000L, Constants.WIRE_RECV)
      .putTag(TraceKeys.HTTP_PATH, "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();

    Span client = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996403000L, Constants.WIRE_RECV, frontend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.CLIENT_RECV, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/api", frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("clnt/finagle.version", "6.45.0", frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, backend))
      .build();

    assertThat(Span2Converter.toSpan(simpleClient))
      .isEqualTo(client);
    assertThat(Span2Converter.fromSpan(client))
      .containsExactly(simpleClient);
  }

  @Test public void client_unfinished() {
    Span2 simpleClient = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .kind(Kind.CLIENT)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .addAnnotation(1472470996238000L, Constants.WIRE_SEND)
      .build();

    Span client = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
      .build();

    assertThat(Span2Converter.toSpan(simpleClient))
      .isEqualTo(client);
    assertThat(Span2Converter.fromSpan(client))
      .containsExactly(simpleClient);
  }

  @Test public void client_kindInferredFromAnnotation() {
    Span2 simpleClient = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(1472470996238000L - 1472470996199000L)
      .addAnnotation(1472470996199000L, Constants.CLIENT_SEND)
      .build();

    Span client = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(1472470996238000L - 1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.CLIENT_RECV, frontend))
      .build();

    assertThat(Span2Converter.toSpan(simpleClient))
      .isEqualTo(client);
  }

  @Test public void noAnnotationsExceptAddresses() {
    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .localEndpoint(frontend)
      .remoteEndpoint(backend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .build();

    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, backend))
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void fromSpan_redundantAddressAnnotations() {
    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .kind(Kind.CLIENT)
      .name("get")
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .build();

    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.CLIENT_RECV, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, frontend))
      .build();

    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void server() {
    Span2 simpleServer = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .id("216a2aea45d08fc9")
      .name("get")
      .kind(Kind.SERVER)
      .localEndpoint(backend)
      .remoteEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .putTag(TraceKeys.HTTP_PATH, "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();

    Span server = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .id(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.SERVER_RECV, backend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.SERVER_SEND, backend))
      .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/api", backend))
      .addBinaryAnnotation(BinaryAnnotation.create("clnt/finagle.version", "6.45.0", backend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
      .build();

    assertThat(Span2Converter.toSpan(simpleServer))
      .isEqualTo(server);
    assertThat(Span2Converter.fromSpan(server))
      .containsExactly(simpleServer);
  }

  /** Buggy instrumentation can send data with missing endpoints. Make sure we can record it. */
  @Test public void missingEndpoints() {
    Span2 span2 = Span2.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("foo")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .build();

    Span span = Span.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("foo")
      .timestamp(1472470996199000L).duration(207000L)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  /** No special treatment for invalid core annotations: missing endpoint */
  @Test public void missingEndpoints_coreAnnotation() {
    Span2 span2 = Span2.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("foo")
      .timestamp(1472470996199000L)
      .addAnnotation(1472470996199000L, "sr")
      .build();

    Span span = Span.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("foo")
      .timestamp(1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, "sr", null))
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void localSpan_emptyComponent() {
    Span2 simpleLocal = Span2.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("local")
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .build();

    Span local = Span.builder()
      .traceId(1L)
      .parentId(1L)
      .id(2L)
      .name("local")
      .timestamp(1472470996199000L).duration(207000L)
      .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "", frontend)).build();

    assertThat(Span2Converter.toSpan(simpleLocal))
      .isEqualTo(local);
    assertThat(Span2Converter.fromSpan(local))
      .containsExactly(simpleLocal);
  }

  @Test public void clientAndServer() {
    Span shared = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, backend))
      .addAnnotation(Annotation.create(1472470996350000L, Constants.SERVER_SEND, backend))
      .addAnnotation(Annotation.create(1472470996403000L, Constants.WIRE_RECV, frontend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.CLIENT_RECV, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/api", frontend))
      .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/backend", backend))
      .addBinaryAnnotation(BinaryAnnotation.create("clnt/finagle.version", "6.45.0", frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("srv/finagle.version", "6.44.0", backend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, backend))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get");

    // the client side owns timestamp and duration
    Span2 client = builder.clone()
      .kind(Kind.CLIENT)
      .localEndpoint(frontend)
      .remoteEndpoint(backend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(1472470996238000L, Constants.WIRE_SEND)
      .addAnnotation(1472470996403000L, Constants.WIRE_RECV)
      .putTag(TraceKeys.HTTP_PATH, "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();

    // notice server tags are different than the client, and the client's annotations aren't here
    Span2 server = builder.clone()
      .kind(Kind.SERVER)
      .shared(true)
      .localEndpoint(backend)
      .remoteEndpoint(frontend)
      .timestamp(1472470996250000L)
      .duration(100000L)
      .putTag(TraceKeys.HTTP_PATH, "/backend")
      .putTag("srv/finagle.version", "6.44.0")
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(client, server);
  }

  /**
   * The old span format had no means of saying it is shared or not. This uses lack of timestamp as
   * a signal
   */
  @Test public void assumesServerWithoutTimestampIsShared() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, backend))
      .addAnnotation(Annotation.create(1472470996350000L, Constants.SERVER_SEND, backend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get")
      .kind(Kind.SERVER)
      .shared(true)
      .localEndpoint(backend)
      .timestamp(1472470996250000L)
      .duration(100000L)
      .build();

    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void clientAndServer_loopback() {
    Span shared = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, frontend))
      .addAnnotation(Annotation.create(1472470996350000L, Constants.SERVER_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.CLIENT_RECV, frontend))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get");

    Span2 client = builder.clone()
      .kind(Kind.CLIENT)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .build();

    Span2 server = builder.clone()
      .kind(Kind.SERVER)
      .shared(true)
      .localEndpoint(frontend)
      .timestamp(1472470996250000L)
      .duration(100000L)
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(client, server);
  }

  @Test public void oneway_loopback() {
    Span shared = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, frontend))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("get");

    Span2 client = builder.clone()
      .kind(Kind.CLIENT)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .build();

    Span2 server = builder.clone()
      .kind(Kind.SERVER)
      .shared(true)
      .localEndpoint(frontend)
      .timestamp(1472470996250000L)
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(client, server);
  }

  @Test public void producer() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_SEND, frontend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.PRODUCER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .build();

    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void producer_remote() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .timestamp(1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_SEND, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.MESSAGE_ADDR, kafka))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.PRODUCER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .remoteEndpoint(kafka)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void producer_duration() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .timestamp(1472470996199000L)
      .duration(51000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996250000L, Constants.WIRE_SEND, frontend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.PRODUCER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(51000L)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void consumer() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .timestamp(1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_RECV, frontend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.CONSUMER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void consumer_remote() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .timestamp(1472470996199000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_RECV, frontend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.MESSAGE_ADDR, kafka))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.CONSUMER)
      .localEndpoint(frontend)
      .remoteEndpoint(kafka)
      .timestamp(1472470996199000L)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  @Test public void consumer_duration() {
    Span span = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("send")
      .timestamp(1472470996199000L)
      .duration(51000L)
      .addAnnotation(Annotation.create(1472470996199000L, Constants.WIRE_RECV, frontend))
      .addAnnotation(Annotation.create(1472470996250000L, Constants.MESSAGE_RECV, frontend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("send")
      .kind(Kind.CONSUMER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(51000L)
      .build();

    assertThat(Span2Converter.toSpan(span2))
      .isEqualTo(span);
    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }

  /** shared span IDs for messaging spans isn't supported, but shouldn't break */
  @Test public void producerAndConsumer() {
    Span shared = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("whatev")
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996403000L, Constants.WIRE_RECV, backend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.MESSAGE_RECV, backend))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.MESSAGE_ADDR, kafka))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("whatev");

    Span2 producer = builder.clone()
      .kind(Kind.PRODUCER)
      .localEndpoint(frontend)
      .remoteEndpoint(kafka)
      .timestamp(1472470996199000L)
      .duration(1472470996238000L - 1472470996199000L)
      .build();

    Span2 consumer = builder.clone()
      .kind(Kind.CONSUMER)
      .shared(true)
      .localEndpoint(backend)
      .remoteEndpoint(kafka)
      .timestamp(1472470996403000L)
      .duration(1472470996406000L - 1472470996403000L)
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(producer, consumer);
  }

  /** shared span IDs for messaging spans isn't supported, but shouldn't break */
  @Test public void producerAndConsumer_loopback_shared() {
    Span shared = Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("message")
      .addAnnotation(Annotation.create(1472470996199000L, Constants.MESSAGE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
      .addAnnotation(Annotation.create(1472470996403000L, Constants.WIRE_RECV, frontend))
      .addAnnotation(Annotation.create(1472470996406000L, Constants.MESSAGE_RECV, frontend))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .name("message");

    Span2 producer = builder.clone()
      .kind(Kind.PRODUCER)
      .localEndpoint(frontend)
      .timestamp(1472470996199000L)
      .duration(1472470996238000L - 1472470996199000L)
      .build();

    Span2 consumer = builder.clone()
      .kind(Kind.CONSUMER)
      .shared(true)
      .localEndpoint(frontend)
      .timestamp(1472470996403000L)
      .duration(1472470996406000L - 1472470996403000L)
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(producer, consumer);
  }

  @Test public void dataMissingEndpointGoesOnFirstSpan() {
    Span shared = Span.builder()
      .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("missing")
      .addAnnotation(Annotation.create(1472470996199000L, "foo", frontend))
      .addAnnotation(Annotation.create(1472470996238000L, "bar", frontend))
      .addAnnotation(Annotation.create(1472470996250000L, "baz", backend))
      .addAnnotation(Annotation.create(1472470996350000L, "qux", backend))
      .addAnnotation(Annotation.create(1472470996403000L, "missing", null))
      .addBinaryAnnotation(BinaryAnnotation.create("foo", "bar", frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("baz", "qux", backend))
      .addBinaryAnnotation(BinaryAnnotation.create("missing", "", null))
      .build();

    Span2.Builder builder = Span2.builder()
      .traceId("216a2aea45d08fc9")
      .id("5b4185666d50f68b")
      .name("missing");

    Span2 first = builder.clone()
      .localEndpoint(frontend)
      .addAnnotation(1472470996199000L, "foo")
      .addAnnotation(1472470996238000L, "bar")
      .addAnnotation(1472470996403000L, "missing")
      .putTag("foo", "bar")
      .putTag("missing", "")
      .build();

    Span2 second = builder.clone()
      .localEndpoint(backend)
      .addAnnotation(1472470996250000L, "baz")
      .addAnnotation(1472470996350000L, "qux")
      .putTag("baz", "qux")
      .build();

    assertThat(Span2Converter.fromSpan(shared))
      .containsExactly(first, second);
  }

  // test converted from stackdriver-zipkin
  @Test public void convertBinaryAnnotations() {
    byte[] boolBuffer = ByteBuffer.allocate(1).put((byte) 1).array();
    byte[] shortBuffer = ByteBuffer.allocate(2).putShort((short) 20).array();
    byte[] intBuffer = ByteBuffer.allocate(4).putInt(32800).array();
    byte[] longBuffer = ByteBuffer.allocate(8).putLong(2147483700L).array();
    byte[] doubleBuffer = ByteBuffer.allocate(8).putDouble(3.1415).array();
    byte[] bytesBuffer = "any carnal pleasure".getBytes(Util.UTF_8);
    Span span = Span.builder()
      .traceId(1)
      .name("test")
      .id(2)
      .addBinaryAnnotation(BinaryAnnotation.create("bool", boolBuffer, Type.BOOL, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("short", shortBuffer, Type.I16, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("int", intBuffer, Type.I32, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("long", longBuffer, Type.I64, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("double", doubleBuffer, Type.DOUBLE, frontend))
      .addBinaryAnnotation(BinaryAnnotation.create("bytes", bytesBuffer, Type.BYTES, frontend))
      .build();

    Span2 span2 = Span2.builder()
      .traceId(1)
      .name("test")
      .id(2)
      .localEndpoint(frontend)
      .putTag("bool", "true")
      .putTag("short", "20")
      .putTag("int", "32800")
      .putTag("long", "2147483700")
      .putTag("double", "3.1415")
      .putTag("bytes", "YW55IGNhcm5hbCBwbGVhc3VyZQ==") // from https://en.wikipedia.org/wiki/Base64
      .build();

    assertThat(Span2Converter.fromSpan(span))
      .containsExactly(span2);
  }
}
