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
package zipkin.minimal;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.server.ZipkinServer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "spring.config.name=zipkin-server"
)
@RunWith(SpringRunner.class)
public class ZipkinServerTest {

  @LocalServerPort int zipkinPort;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test public void readsBackSpanName() throws Exception {
    String service = "web";
    Endpoint endpoint = Endpoint.create(service, 127 << 24 | 1, 80);
    Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, "sr", endpoint);
    Span span = Span.builder().id(1L).traceId(1L).name("get").addAnnotation(ann).build();

    // write the span to the server
    Response post = post("/api/v1/spans", Codec.JSON.writeSpans(asList(span)));
    assertThat(post.isSuccessful()).isTrue();

    // sleep as the the storage operation is async
    Thread.sleep(1000);

    // read back the span name, given its service
    Response get = get("/api/v1/spans?serviceName=" + service);
    assertThat(get.isSuccessful()).isTrue();
    assertThat(get.body().string())
      .isEqualTo("[\"" + span.name + "\"]");
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .post(RequestBody.create(null, body))
      .build()).execute();
  }
}
