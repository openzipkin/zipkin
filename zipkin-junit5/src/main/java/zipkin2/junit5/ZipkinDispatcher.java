/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.junit5;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.GzipSource;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.storage.StorageComponent;

final class ZipkinDispatcher extends Dispatcher {
  private final Collector consumer;
  private final CollectorMetrics metrics;
  private final MockWebServer server;

  ZipkinDispatcher(StorageComponent storage, CollectorMetrics metrics, MockWebServer server) {
    this.consumer = Collector.newBuilder(getClass()).storage(storage).metrics(metrics).build();
    this.metrics = metrics;
    this.server = server;
  }

  @Override
  public MockResponse dispatch(RecordedRequest request) {
    HttpUrl url = server.url(request.getPath());
    if (request.getMethod().equals("POST")) {
      String type = request.getHeader("Content-Type");
      if (url.encodedPath().equals("/api/v1/spans")) {
        SpanBytesDecoder decoder =
          type != null && type.contains("/x-thrift")
            ? SpanBytesDecoder.THRIFT
            : SpanBytesDecoder.JSON_V1;
        return acceptSpans(request, decoder);
      } else if (url.encodedPath().equals("/api/v2/spans")) {
        SpanBytesDecoder decoder =
          type != null && type.contains("/x-protobuf")
            ? SpanBytesDecoder.PROTO3
            : SpanBytesDecoder.JSON_V2;
        return acceptSpans(request, decoder);
      }
    } else { // unsupported method
      return new MockResponse().setResponseCode(405);
    }
    return new MockResponse().setResponseCode(404);
  }

  MockResponse acceptSpans(RecordedRequest request, SpanBytesDecoder decoder) {
    byte[] body = request.getBody().readByteArray();
    metrics.incrementMessages();
    String encoding = request.getHeader("Content-Encoding");
    if (encoding != null && encoding.contains("gzip")) {
      try {
        Buffer result = new Buffer();
        GzipSource source = new GzipSource(new Buffer().write(body));
        while (source.read(result, Integer.MAX_VALUE) != -1) ;
        body = result.readByteArray();
      } catch (IOException e) {
        metrics.incrementMessagesDropped();
        return new MockResponse().setResponseCode(400).setBody("Cannot gunzip spans");
      }
    }
    metrics.incrementBytes(body.length);

    final MockResponse result = new MockResponse();
    if (body.length == 0) return result.setResponseCode(202); // lenient on empty

    consumer.acceptSpans(body, decoder, new Callback<Void>() {
      @Override public void onSuccess(Void value) {
        result.setResponseCode(202);
      }

      @Override public void onError(Throwable t) {
        String message = t.getMessage();
        result.setBody(message).setResponseCode(message.startsWith("Cannot store") ? 500 : 400);
      }
    });
    return result;
  }
}
