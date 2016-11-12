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
package zipkin.execjar;

import java.io.IOException;
import java.util.Collections;
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
import zipkin.internal.Util;

import static org.junit.Assert.assertEquals;

public class StrictTraceIdFalseTest {
  OkHttpClient client = new OkHttpClient();

  @Rule
  public ExecJarRule zipkin = new ExecJarRule()
      .putEnvironment("STRICT_TRACE_ID", "false");

  @Test
  public void canSearchByLower64Bits() throws IOException {
    Span span = Span.builder().traceIdHigh(1L).traceId(2L).id(2L).name("test-span")
        .addAnnotation(Annotation.create(System.currentTimeMillis() * 1000L, "hello",
            Endpoint.create("foo-service", 127 << 24 | 1))).build();

    byte[] spansInJson = Codec.JSON.writeSpans(Collections.singletonList(span));

    client.newCall(new Request.Builder()
        .url("http://localhost:" + zipkin.port() + "/api/v1/spans")
        .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build())
        .execute();

    Response response = client.newCall(new Request.Builder()
        .url("http://localhost:" + zipkin.port() + "/api/v1/trace/" + Util.toLowerHex(span.traceId))
        .build()).execute();

    assertEquals(span, Codec.JSON.readSpans(response.body().bytes()).get(0));
  }
}
