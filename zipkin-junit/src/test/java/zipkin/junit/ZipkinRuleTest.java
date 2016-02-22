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
import static zipkin.Constants.SERVER_RECV;

public class ZipkinRuleTest {

  @Rule
  public ZipkinRule zipkin = new ZipkinRule();

  OkHttpClient client = new OkHttpClient();

  String service = "web";
  Endpoint endpoint = Endpoint.create(service, 127 << 24 | 1, 80);
  Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, endpoint);
  Span span = new Span.Builder().id(1L).traceId(1L).name("get").addAnnotation(ann).build();

  @Test
  public void getTraces_storedUsingHttp() throws IOException {
    // write the span to the zipkin using http
    byte[] spansInJson = Codec.JSON.writeSpans(asList(span));
    Response postResponse = client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/api/v1/spans")
        .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build()
    ).execute();
    assertThat(postResponse.code()).isEqualTo(202);

    // read the traces directly
    assertThat(zipkin.getTraces())
        .extracting(t -> t.get(0).traceId) // get only the trace id
        .containsOnly(span.traceId);
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
}
