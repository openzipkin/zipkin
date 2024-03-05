/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.junit5;

import java.io.IOException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.ByteString;
import okio.GzipSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

public class ZipkinExtensionTest {

  @RegisterExtension public ZipkinExtension zipkin = new ZipkinExtension();

  List<Span> spans = List.of(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);
  OkHttpClient client = new OkHttpClient();

  @Test void getTraces_storedViaPost() throws IOException {
    // write the span to the zipkin using http
    assertPostSpansV1Success(CLIENT_SPAN);

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(List.of(CLIENT_SPAN));
  }

  @Test void getTraces_storedViaPostVersion2_json() throws IOException {
    getTraces_storedViaPostVersion2("application/json", SpanBytesEncoder.JSON_V2);
  }

  @Test void getTraces_storedViaPostVersion2_proto3() throws IOException {
    getTraces_storedViaPostVersion2("application/x-protobuf", SpanBytesEncoder.PROTO3);
  }

  void getTraces_storedViaPostVersion2(String mediaType, SpanBytesEncoder encoder)
    throws IOException {

    byte[] message = encoder.encodeList(spans);

    // write the span to the zipkin using http api v2
    try (Response response = client.newCall(
      new Request.Builder().url(zipkin.httpUrl() + "/api/v2/spans")
        .post(RequestBody.create(MediaType.parse(mediaType), message))
        .build()).execute()) {
      assertThat(response.code()).isEqualTo(202);
    }

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(List.of(spans.get(0)), List.of(spans.get(1)));
  }

  /** The rule is here to help debugging. Even partial spans should be returned */
  @Test void getTraces_whenMissingTimestamps() throws IOException {
    Span span = Span.newBuilder().traceId("1").id("1").name("foo").build();
    // write the span to the zipkin using http
    assertPostSpansV1Success(span);

    // read the traces directly
    assertThat(zipkin.getTraces()).containsOnly(List.of(span));
  }

  /** The raw query can show affects like redundant rows in the data store. */
  @Test void storeSpans_readbackRaw() {
    Span missingDuration = LOTS_OF_SPANS[0].toBuilder().duration(null).build();
    Span withDuration = LOTS_OF_SPANS[0];

    // write the span to zipkin directly
    zipkin.storeSpans(List.of(missingDuration));
    zipkin.storeSpans(List.of(withDuration));

    assertThat(zipkin.getTrace(missingDuration.traceId())).containsExactly(missingDuration,
      withDuration);
  }

  @Test void httpRequestCountIncrements() throws IOException {
    assertPostSpansV1Success(spans);
    assertPostSpansV1Success(spans);

    assertThat(zipkin.httpRequestCount()).isEqualTo(2);
  }

  /**
   * Normally, a span can be reported twice: for client and server. However, there are bugs that
   * happened where several updates went to the same span id. {@link ZipkinExtension#collectorMetrics}
   * can be used to help ensure a span isn't reported more times than expected.
   */
  @Test void collectorMetrics_spans() throws IOException {
    assertPostSpansV1Success(LOTS_OF_SPANS[0]);
    assertPostSpansV1Success(LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    assertThat(zipkin.collectorMetrics().spans()).isEqualTo(3);
  }

  @Test void postSpans_disconnectDuringBody() throws IOException {
    zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

    try (Response response = postSpansV1(spans)) {
      failBecauseExceptionWasNotThrown(IOException.class);
    } catch (IOException expected) { // not always a ConnectException!
    }

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to disconnect
    assertThat(zipkin.getTraces()).isEmpty();

    // create a new connection pool to avoid flakey tests
    client = new OkHttpClient();

    // The failure shouldn't affect later requests
    assertPostSpansV1Success(spans);
  }

  @Test void postSpans_sendErrorResponse400() throws IOException {
    zipkin.enqueueFailure(HttpFailure.sendErrorResponse(400, "Invalid Format"));

    try (Response response = postSpansV1(spans)) {
      assertThat(response.code()).isEqualTo(400);
      assertThat(response.body().string()).isEqualTo("Invalid Format");
    }

    // Zipkin didn't store the spans, as they shouldn't have been readable, due to the error
    assertThat(zipkin.getTraces()).isEmpty();

    // The failure shouldn't affect later requests
    assertPostSpansV1Success(spans);
  }

  @Test void gzippedSpans() throws IOException {
    byte[] spansInJson = SpanBytesEncoder.JSON_V1.encodeList(spans);

    ByteString gzippedJson;
    try (Buffer sink = new Buffer(); Buffer source = new Buffer()) {
      source.write(spansInJson);
      GzipSink gzipSink = new GzipSink(sink);
      gzipSink.write(source, spansInJson.length);
      gzipSink.close();
      gzippedJson = sink.readByteString();
    }

    try (Response response = client.newCall(
      new Request.Builder().url(zipkin.httpUrl() + "/api/v1/spans")
        .addHeader("Content-Encoding", "gzip")
        .post(RequestBody.create(MediaType.parse("application/json"), gzippedJson))
        .build()).execute()) {
      assertThat(response.code()).isEqualTo(202);
    }

    assertThat(zipkin.collectorMetrics().bytes()).isEqualTo(spansInJson.length);
  }

  Response postSpansV1(List<Span> spans) throws IOException {
    byte[] spansInJson = SpanBytesEncoder.JSON_V1.encodeList(spans);
    return client.newCall(new Request.Builder().url(zipkin.httpUrl() + "/api/v1/spans")
      .post(RequestBody.create(MediaType.parse("application/json"), spansInJson))
      .build()).execute();
  }

  void assertPostSpansV1Success(Span... spans) throws IOException {
    assertPostSpansV1Success(List.of(spans));
  }

  void assertPostSpansV1Success(List<Span> spans) throws IOException {
    try (Response response = postSpansV1(spans)) {
      assertThat(response.code()).isEqualTo(202);
    }
  }
}
