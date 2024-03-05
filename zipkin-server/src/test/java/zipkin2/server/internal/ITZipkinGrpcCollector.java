/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.proto3.ListOfSpans;
import zipkin2.storage.InMemoryStorage;

import static okhttp3.Protocol.H2_PRIOR_KNOWLEDGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.server.internal.ITZipkinServer.url;

/** This tests that we accept messages constructed by other clients. */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
  }
)
class ITZipkinGrpcCollector {
  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  @BeforeEach void init() {
    storage.clear();
  }

  OkHttpClient client = new OkHttpClient.Builder().protocols(List.of(H2_PRIOR_KNOWLEDGE)).build();

  ListOfSpans grpcRequest;

  @BeforeEach void sanityCheckCodecCompatible() throws IOException {
    grpcRequest = ListOfSpans.ADAPTER.decode(SpanBytesEncoder.PROTO3.encodeList(TestObjects.TRACE));

    assertThat(SpanBytesDecoder.PROTO3.decodeList(grpcRequest.encode()))
      .containsExactlyElementsOf(TestObjects.TRACE); // sanity check codec compatible
  }

  @Test void report_trace() throws IOException {
    callReport(grpcRequest); // Result is effectively void

    awaitSpans();

    assertThat(storage.getTraces())
      .containsExactly(TestObjects.TRACE);
  }

  @Test void report_emptyIsOk() throws IOException {
    callReport(new ListOfSpans.Builder().build());
  }

  void callReport(ListOfSpans spans) throws IOException {
    try (Buffer requestBody = new Buffer(); Buffer encodedMessage = new Buffer()) {
      requestBody.writeByte(0 /* compressedFlag */);

      ListOfSpans.ADAPTER.encode(encodedMessage, spans);
      requestBody.writeInt((int) encodedMessage.size());
      requestBody.writeAll(encodedMessage);

      Request request = new Request.Builder()
        .url(url(server, "/zipkin.proto3.SpanService/Report"))
        .addHeader("te", "trailers")
        .post(RequestBody.create(requestBody.snapshot(), MediaType.get("application/grpc")))
        .build();
      try (Response response = client.newCall(request).execute();
           BufferedSource responseBody = response.body().source()) {

        // We expect this is a valid gRPC over HTTP2 response (Length-Prefixed-Message).
        // See https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#responses
        byte compressedFlag = responseBody.readByte();
        long messageLength = responseBody.readInt() & 0xffffffffL;
        assertThat(responseBody.exhausted()).isTrue(); // We expect a single response

        // Now, verify the Length-Prefixed-Message
        assertThat(compressedFlag).isZero(); // server didn't compress
        assertThat(messageLength).isZero(); // there are no fields in ReportResponse
      }
    }
  }

  void awaitSpans() {
    await().untilAsserted(// wait for spans
      () -> assertThat(storage.acceptedSpanCount()).isGreaterThanOrEqualTo(1));
  }
}
