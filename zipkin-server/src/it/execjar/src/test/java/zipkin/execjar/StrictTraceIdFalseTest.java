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
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;

import static org.junit.Assert.assertEquals;

public class StrictTraceIdFalseTest {
  OkHttpClient client = new OkHttpClient();

  @Rule
  public ExecJarRule zipkin = new ExecJarRule()
      .putEnvironment("STRICT_TRACE_ID", "false");

  @Test
  public void canSearchByLower64Bits() throws IOException {
    Span span = Span.newBuilder().traceId("463ac35c9f6413ad48485a3953bb6124").id("a")
      .name("test-span")
      .localEndpoint(Endpoint.newBuilder().serviceName("foo-service").build())
      .addAnnotation(System.currentTimeMillis() * 1000L, "hello").build();

    byte[] spansInJson = SpanBytesEncoder.JSON_V2.encodeList(Collections.singletonList(span));

    client.newCall(new Request.Builder()
        .url("http://localhost:" + zipkin.port() + "/api/v2/spans")
        .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build())
        .execute();

    Response response = client.newCall(new Request.Builder()
        .url("http://localhost:" + zipkin.port() + "/api/v2/trace/" + span.traceId())
        .build()).execute();

    assertEquals(span, SpanBytesDecoder.JSON_V2.decodeList(response.body().bytes()).get(0));
  }
}
