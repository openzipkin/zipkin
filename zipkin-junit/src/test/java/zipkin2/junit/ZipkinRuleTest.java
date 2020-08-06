/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.junit;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

public class ZipkinRuleTest {

  static {
    // ensure jul-to-slf4j works
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  @Rule public ZipkinRule zipkin = new ZipkinRule();

  List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);
  WebClient client;

  @Before
  public void setUp() {
    client = WebClient.of(zipkin.httpUrl());
  }

  @Test
  public void getTraces_storedViaPost() throws IOException {
    List<Span> trace = asList(CLIENT_SPAN);
    // write the span to the zipkin using http
    assertThat(postSpansV1(trace).status()).isEqualTo(HttpStatus.ACCEPTED);

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(trace);
  }

  @Test
  public void getTraces_storedViaPostVersion2_json() throws IOException {
    getTraces_storedViaPostVersion2("application/json", SpanBytesEncoder.JSON_V2);
  }

  @Test
  public void getTraces_storedViaPostVersion2_proto3() throws IOException {
    getTraces_storedViaPostVersion2("application/x-protobuf", SpanBytesEncoder.PROTO3);
  }

  void getTraces_storedViaPostVersion2(String mediaType, SpanBytesEncoder encoder)
    throws IOException {

    byte[] message = encoder.encodeList(spans);

    // write the span to the zipkin using http api v2
    AggregatedHttpResponse response =
      client.execute(
        RequestHeaders.of(HttpMethod.POST, "/api/v2/spans", HttpHeaderNames.CONTENT_TYPE,
          mediaType), message).aggregate().join();
    assertThat(response.status()).isEqualTo(HttpStatus.ACCEPTED);

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(asList(spans.get(0)), asList(spans.get(1)));
  }

  /** The rule is here to help debugging. Even partial spans should be returned */
  @Test
  public void getTraces_whenMissingTimestamps() throws IOException {
    Span span = Span.newBuilder().traceId("1").id("1").name("foo").build();
    // write the span to the zipkin using http
    assertThat(postSpansV1(asList(span)).status()).isEqualTo(HttpStatus.ACCEPTED);

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(asList(span));
  }

  /** The raw query can show affects like redundant rows in the data store. */
  @Test
  public void storeSpans_readbackRaw() {
    Span missingDuration = LOTS_OF_SPANS[0].toBuilder().duration(null).build();
    Span withDuration = LOTS_OF_SPANS[0];

    // write the span to zipkin directly
    zipkin.storeSpans(asList(missingDuration));
    zipkin.storeSpans(asList(withDuration));

    assertThat(zipkin.getTrace(missingDuration.traceId()))
      .containsExactly(missingDuration, withDuration);
  }

  @Test
  public void httpRequestCountIncrements() throws IOException {
    postSpansV1(spans);
    postSpansV1(spans);

    assertThat(zipkin.httpRequestCount()).isEqualTo(2);
  }

  /**
   * Normally, a span can be reported twice: for client and server. However, there are bugs that
   * happened where several updates went to the same span id. {@link ZipkinRule#collectorMetrics}
   * can be used to help ensure a span isn't reported more times than expected.
   */
  @Test
  public void collectorMetrics_spans() throws IOException {
    postSpansV1(asList(LOTS_OF_SPANS[0]));
    postSpansV1(asList(LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]));

    assertThat(zipkin.collectorMetrics().spans()).isEqualTo(3);
  }

  @Test
  public void postSpans_disconnectDuringBody() {
    zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

    postSpansV1(spans);

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to disconnect
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpansV1(spans).status()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  public void postSpans_sendErrorResponse400() throws IOException {
    zipkin.enqueueFailure(HttpFailure.sendErrorResponse(400, "Invalid Format"));

    AggregatedHttpResponse response = postSpansV1(spans);
    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.contentUtf8()).isEqualTo("Invalid Format");

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to the error
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertThat(postSpansV1(spans).status()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  public void gzippedSpans() throws IOException {
    byte[] spansInJson = SpanBytesEncoder.JSON_V1.encodeList(spans);

    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(sink)) {
      gzip.write(spansInJson);
    }
    byte[] gzippedJson = sink.toByteArray();

    client.execute(
      RequestHeaders.of(HttpMethod.POST, "/api/v1/spans", HttpHeaderNames.CONTENT_ENCODING, "gzip",
        HttpHeaderNames.CONTENT_TYPE, "application/json"), gzippedJson).aggregate().join();

    assertThat(zipkin.collectorMetrics().bytes()).isEqualTo(spansInJson.length);
  }

  AggregatedHttpResponse postSpansV1(List<Span> spans) {
    byte[] spansInJson = SpanBytesEncoder.JSON_V1.encodeList(spans);
    return client.execute(
      RequestHeaders.of(HttpMethod.POST, "/api/v1/spans", HttpHeaderNames.CONTENT_TYPE,
        "application/json"), spansInJson).aggregate().join();
  }
}
