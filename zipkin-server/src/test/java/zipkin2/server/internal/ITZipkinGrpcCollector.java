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
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.ReportResponse;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
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
    "zipkin.collector.grpc.enabled=true"
  }
)
@RunWith(SpringRunner.class)
public class ITZipkinGrpcCollector {
  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  @Before public void init() {
    storage.clear();
  }

  OkHttpClient client = new OkHttpClient.Builder().protocols(asList(H2_PRIOR_KNOWLEDGE)).build();

  ListOfSpans request;

  @Before public void sanityCheckCodecCompatible() throws IOException {
    request = ListOfSpans.ADAPTER.decode(SpanBytesEncoder.PROTO3.encodeList(TestObjects.TRACE));

    assertThat(SpanBytesDecoder.PROTO3.decodeList(request.encode()))
      .containsExactlyElementsOf(TestObjects.TRACE); // sanity check codec compatible
  }

  @Test public void report_trace() throws IOException {
    callReport(request); // Result is effectively void

    awaitSpans();

    assertThat(storage.getTraces())
      .containsExactly(TestObjects.TRACE);
  }

  @Test public void report_emptyIsOk() throws IOException {

    callReport(new ListOfSpans.Builder().build());
  }

  ReportResponse callReport(ListOfSpans spans) throws IOException {
    Buffer requestBody = new Buffer();
    requestBody.writeByte(0 /* compressedFlag */);
    Buffer encodedMessage = new Buffer();
    ListOfSpans.ADAPTER.encode(encodedMessage, spans);
    requestBody.writeInt((int) encodedMessage.size());
    requestBody.writeAll(encodedMessage);

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/zipkin.proto3.SpanService/Report"))
      .addHeader("te", "trailers")
      .post(RequestBody.create(requestBody.snapshot(), MediaType.get("application/grpc")))
      .build())
      .execute();

    BufferedSource responseBody = response.body().source();
    assertThat((int) responseBody.readByte()).isEqualTo(0); // uncompressed
    long encodedLength = responseBody.readInt() & 0xffffffffL;

    return ReportResponse.ADAPTER.decode(responseBody);
  }

  void awaitSpans() {
    await().untilAsserted(// wait for spans
      () -> assertThat(storage.acceptedSpanCount()).isGreaterThanOrEqualTo(1));
  }
}
