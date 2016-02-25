/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.junit;

import java.io.IOException;
import java.net.ConnectException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.internal.Util.gzip;

public class ZipkinRuleTest {

  @Rule
  public ZipkinRule zipkin = new ZipkinRule();

  OkHttpClient client = new OkHttpClient();

  String service = "web";
  Endpoint endpoint = Endpoint.create(service, 127 << 24 | 1, 80);
  Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, endpoint);
  Span span = new Span.Builder().id(1L).traceId(1L).timestamp(ann.timestamp).name("get")
      .addAnnotation(ann).build();

  @Test
  public void getTraces_storedViaPost() throws IOException {
    // write the span to the zipkin using http
    assertThat(postSpans(span).code()).isEqualTo(202);

    // read the traces directly
    assertThat(zipkin.getTraces())
        .containsOnly(asList(span));
  }

  @Test
  public void healthIsOK() throws IOException {
    Response getResponse = client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() +"/health").build()
    ).execute();

    assertThat(getResponse.code()).isEqualTo(200);
    assertThat(getResponse.body().string()).isEqualTo("OK\n");
  }

  @Test
  public void storeSpans_readbackHttp() throws IOException {
    // write the span to zipkin directly
    zipkin.storeSpans(asList(span));

    // read trace id using the the http api
    Response getResponse = client.newCall(new Request.Builder()
        .url(String.format("%s/api/v1/trace/%016x", zipkin.httpUrl(), span.traceId)).build()
    ).execute();

    assertThat(getResponse.code()).isEqualTo(200);
  }

  @Test
  public void httpRequestCountIncrements() throws IOException {
    postSpans(span);
    postSpans(span);

    assertThat(zipkin.httpRequestCount()).isEqualTo(2);
  }

  @Test
  public void postSpans_disconnectDuringBody() throws IOException {
    zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

    try {
      postSpans(span);
      failBecauseExceptionWasNotThrown(ConnectException.class);
    } catch (ConnectException expected) {
    }

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to disconnect
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpans(span).code()).isEqualTo(202);
  }

  @Test
  public void postSpans_sendErrorResponse400() throws IOException {
    zipkin.enqueueFailure(HttpFailure.sendErrorResponse(400, "Invalid Format"));

    Response response = postSpans(span);
    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string()).isEqualTo("Invalid Format");

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to the error
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpans(span).code()).isEqualTo(202);
  }

  private Response postSpans(Span ... spans) throws IOException {
    byte[] spansInJson = Codec.JSON.writeSpans(asList(spans));
    return client.newCall(new Request.Builder()
          .url(zipkin.httpUrl() + "/api/v1/spans")
          .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build()
      ).execute();
  }

  @Test
  public void gzippedSpans() throws IOException {
    byte[] spansInJson = Codec.JSON.writeSpans(asList(span));
    byte[] gzippedJson = gzip(spansInJson);

    client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/api/v1/spans")
        .addHeader("Content-Encoding", "gzip")
        .post(RequestBody.create(MediaType.parse("application/json"), gzippedJson)).build()
    ).execute();

    assertThat(zipkin.getTraces()).containsOnly(asList(span));
  }

  @Test
  public void gzippedSpans_invalidIs400() throws IOException {
    Response response = client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/api/v1/spans")
        .addHeader("Content-Encoding", "gzip")
        .post(RequestBody.create(MediaType.parse("application/json"), "hello".getBytes())).build()
    ).execute();

    assertThat(response.code()).isEqualTo(400);
  }
}
