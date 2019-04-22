/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin.minimal;

import java.io.IOException;
import java.util.Collections;
import com.linecorp.armeria.server.Server;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {"spring.config.name=zipkin-server", "server.port=0"}
)
@RunWith(SpringRunner.class)
public class ZipkinServerTest {

  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test public void readsBackNames() throws Exception {
    String service = "web";
    Span span = Span.newBuilder().traceId("463ac35c9f6413ad48485a3953bb6124").id("a")
      .name("test-span")
      .localEndpoint(Endpoint.newBuilder().serviceName(service).build())
      .remoteEndpoint(Endpoint.newBuilder().serviceName("app").build())
      .addAnnotation(System.currentTimeMillis() * 1000L, "hello").build();

    byte[] spansInJson = SpanBytesEncoder.JSON_V2.encodeList(Collections.singletonList(span));

    // write the span to the server
    Response post = post("/api/v2/spans", spansInJson);
    assertThat(post.isSuccessful()).isTrue();

    // sleep as the the storage operation is async
    Thread.sleep(1000);

    // read back the span name, given its service
    Response get = get("/api/v2/spans?serviceName=" + service);
    assertThat(get.isSuccessful()).isTrue();
    assertThat(get.body().string())
      .isEqualTo("[\"" + span.name() + "\"]");

    // read back the remote service name, given its service
    get = get("/api/v2/remoteServices?serviceName=" + service);
    assertThat(get.isSuccessful()).isTrue();
    assertThat(get.body().string())
      .isEqualTo("[\"" + span.remoteServiceName() + "\"]");
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + server.activePort().get().localAddress().getPort() + path)
      .build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + server.activePort().get().localAddress().getPort() + path)
      .post(RequestBody.create(null, body))
      .build()).execute();
  }
}
