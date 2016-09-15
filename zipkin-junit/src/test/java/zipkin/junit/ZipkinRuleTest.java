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
import java.util.Arrays;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.ByteString;
import okio.GzipSink;
import okio.GzipSource;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Span;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin.TestObjects.TRACE;

public class ZipkinRuleTest {

  @Rule
  public ZipkinRule zipkin = new ZipkinRule();

  OkHttpClient client = new OkHttpClient();

  long traceId = TRACE.get(0).traceId;

  @Test
  public void getTraces_storedViaPost() throws IOException {
    // write the span to the zipkin using http
    assertThat(postSpans(TRACE).code()).isEqualTo(202);

    // read the traces directly
    assertThat(zipkin.getTraces())
        .containsOnly(TRACE);
  }

  @Test
  public void healthIsOK() throws IOException {
    Response getResponse = client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/health").build()
    ).execute();

    assertThat(getResponse.code()).isEqualTo(200);
    assertThat(getResponse.body().string()).isEqualTo("OK\n");
  }

  @Test
  public void storeSpans_readbackHttp() throws IOException {
    // write the span to zipkin directly
    zipkin.storeSpans(TRACE);

    // read trace id using the the http api
    Response getResponse = client.newCall(new Request.Builder()
        .url(format("%s/api/v1/trace/%016x", zipkin.httpUrl(), traceId)).build()
    ).execute();

    assertThat(getResponse.code()).isEqualTo(200);
  }

  /** The raw query can show affects like redundant rows in the data store. */
  @Test
  public void storeSpans_readbackRaw() throws IOException {
    // write the span to zipkin directly
    zipkin.storeSpans(TRACE);
    zipkin.storeSpans(TRACE);

    // Default will merge by span id
    Response defaultResponse = client.newCall(new Request.Builder()
        .url(format("%s/api/v1/trace/%016x", zipkin.httpUrl(), traceId)).build()
    ).execute();

    assertThat(Codec.JSON.readSpans(defaultResponse.body().bytes())).hasSize(TRACE.size());

    // In the in-memory (or cassandra) stores, a raw read will show duplicate span rows.
    Response rawResponse = client.newCall(new Request.Builder()
        .url(format("%s/api/v1/trace/%016x?raw", zipkin.httpUrl(), traceId)).build()
    ).execute();

    assertThat(Codec.JSON.readSpans(rawResponse.body().bytes())).hasSize(TRACE.size() * 2);
  }

  @Test
  public void downgrades128BitTraceIdToLower64Bits() throws Exception {
    zipkin.storeSpans(TRACE);

    Response getResponse = client.newCall(new Request.Builder()
        .url(format("%s/api/v1/trace/48485a3953bb6124%016x", zipkin.httpUrl(), traceId)).build()
    ).execute();

    assertThat(getResponse.code()).isEqualTo(200);
  }

  @Test
  public void httpRequestCountIncrements() throws IOException {
    postSpans(TRACE);
    postSpans(TRACE);

    assertThat(zipkin.httpRequestCount()).isEqualTo(2);
  }

  /**
   * Normally, a span can be reported twice: for client and server. However, there are bugs that
   * happened where several updates went to the same span id. {@link ZipkinRule#collectorMetrics}
   * can be used to help ensure a span isn't reported more times than expected.
   */
  @Test
  public void collectorMetrics_spans() throws IOException {
    postSpans(TRACE);
    postSpans(TRACE);

    assertThat(zipkin.collectorMetrics().spans()).isEqualTo(TRACE.size() * 2);
  }

  @Test
  public void postSpans_disconnectDuringBody() throws IOException {
    zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

    try {
      postSpans(TRACE);
      failBecauseExceptionWasNotThrown(IOException.class);
    } catch (IOException expected) { // not always a ConnectException!
    }

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to disconnect
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpans(TRACE).code()).isEqualTo(202);
  }

  @Test
  public void postSpans_sendErrorResponse400() throws IOException {
    zipkin.enqueueFailure(HttpFailure.sendErrorResponse(400, "Invalid Format"));

    Response response = postSpans(TRACE);
    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string()).isEqualTo("Invalid Format");

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to the error
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpans(TRACE).code()).isEqualTo(202);
  }

  @Test
  public void gzippedSpans() throws IOException {
    byte[] spansInJson = Codec.JSON.writeSpans(TRACE);

    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(new Buffer().write(spansInJson), spansInJson.length);
    gzipSink.close();
    ByteString gzippedJson = sink.readByteString();

    client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/api/v1/spans")
        .addHeader("Content-Encoding", "gzip")
        .post(RequestBody.create(MediaType.parse("application/json"), gzippedJson)).build()
    ).execute();

    assertThat(zipkin.getTraces()).containsOnly(TRACE);
    assertThat(zipkin.collectorMetrics().bytes()).isEqualTo(spansInJson.length);
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

  @Test
  public void readSpans_gzippedResponse() throws Exception {
    char[] annotation2K = new char[2048];
    Arrays.fill(annotation2K, 'a');

    List<Span> trace = asList(TRACE.get(0).toBuilder().addAnnotation(
            Annotation.create(System.currentTimeMillis(), new String(annotation2K), null)).build());

    zipkin.storeSpans(trace);

    Response response = client.newCall(new Request.Builder()
            .url(format("%s/api/v1/trace/%016x", zipkin.httpUrl(), traceId))
            .addHeader("Accept-Encoding", "gzip").build()
    ).execute();

    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().contentLength()).isLessThan(annotation2K.length);

    Buffer result = new Buffer();
    GzipSource source = new GzipSource(response.body().source());
    while (source.read(result, Integer.MAX_VALUE) != -1) ;
    byte[] unzipped = result.readByteArray();

    assertThat(Codec.JSON.readSpans(unzipped)).isEqualTo(trace);
  }

  Response postSpans(List<Span> spans) throws IOException {
    byte[] spansInJson = Codec.JSON.writeSpans(spans);
    return client.newCall(new Request.Builder()
        .url(zipkin.httpUrl() + "/api/v1/spans")
        .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build()
    ).execute();
  }
}
