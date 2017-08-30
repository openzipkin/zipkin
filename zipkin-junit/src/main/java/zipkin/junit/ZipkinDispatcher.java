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
package zipkin.junit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.GzipSource;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;
import zipkin.internal.V2JsonSpanDecoder;
import zipkin.internal.V2StorageComponent;
import zipkin.internal.v2.codec.Encoder;
import zipkin.internal.v2.internal.Platform;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;

import static zipkin.internal.Util.lowerHexToUnsignedLong;

final class ZipkinDispatcher extends Dispatcher {
  static final long DEFAULT_LOOKBACK = 86400000L; // 1 day in millis
  static final SpanDecoder JSON2_DECODER = new V2JsonSpanDecoder();

  private final SpanStore store;
  private final zipkin.internal.v2.storage.SpanStore store2;
  private final Collector consumer;
  private final CollectorMetrics metrics;
  private final MockWebServer server;

  ZipkinDispatcher(V2StorageComponent storage, CollectorMetrics metrics, MockWebServer server) {
    this.store = storage.spanStore();
    this.store2 = storage.v2SpanStore();
    this.consumer = Collector.builder(getClass()).storage(storage).metrics(metrics).build();
    this.metrics = metrics;
    this.server = server;
  }

  @Override
  public MockResponse dispatch(RecordedRequest request) {
    HttpUrl url = server.url(request.getPath());
    if (request.getMethod().equals("GET")) {
      if (url.encodedPath().equals("/health")) {
        return new MockResponse().setBody("OK\n");
      } else if (url.encodedPath().startsWith("/api/v1/")) {
        return queryV1(url);
      } else if (url.encodedPath().startsWith("/api/v2/")) {
        try {
          return queryV2(url);
        } catch (IOException e) {
          throw Platform.get().uncheckedIOException(e);
        }
      }
    } else if (request.getMethod().equals("POST")) {
      if (url.encodedPath().equals("/api/v1/spans")) {
        String type = request.getHeader("Content-Type");
        SpanDecoder decoder = type != null && type.contains("/x-thrift")
          ? SpanDecoder.THRIFT_DECODER
          : SpanDecoder.JSON_DECODER;
        return acceptSpans(request, decoder);
      } else if (url.encodedPath().equals("/api/v2/spans")) {
        return acceptSpans(request, JSON2_DECODER);
      }
    } else { // unsupported method
      return new MockResponse().setResponseCode(405);
    }
    return new MockResponse().setResponseCode(404);
  }

  MockResponse queryV1(HttpUrl url) {
    if (url.encodedPath().equals("/api/v1/services")) {
      return jsonResponse(Codec.JSON.writeStrings(store.getServiceNames()));
    } else if (url.encodedPath().equals("/api/v1/spans")) {
      String serviceName = url.queryParameter("serviceName");
      return jsonResponse(Codec.JSON.writeStrings(store.getSpanNames(serviceName)));
    } else if (url.encodedPath().equals("/api/v1/dependencies")) {
      Long endTs = maybeLong(url.queryParameter("endTs"));
      Long lookback = maybeLong(url.queryParameter("lookback"));
      List<DependencyLink> result = store.getDependencies(endTs, lookback);
      return jsonResponse(Codec.JSON.writeDependencyLinks(result));
    } else if (url.encodedPath().equals("/api/v1/traces")) {
      QueryRequest queryRequest = toQueryRequest(url);
      return jsonResponse(Codec.JSON.writeTraces(store.getTraces(queryRequest)));
    } else if (url.encodedPath().startsWith("/api/v1/trace/")) {
      String traceIdHex = url.encodedPath().replace("/api/v1/trace/", "");
      long traceIdHigh = traceIdHex.length() == 32 ? lowerHexToUnsignedLong(traceIdHex, 0) : 0L;
      long traceIdLow = lowerHexToUnsignedLong(traceIdHex);
      List<Span> trace = url.queryParameterNames().contains("raw")
        ? store.getRawTrace(traceIdHigh, traceIdLow)
        : store.getTrace(traceIdHigh, traceIdLow);
      if (trace != null) return jsonResponse(Codec.JSON.writeSpans(trace));
    }
    return new MockResponse().setResponseCode(404);
  }

  MockResponse queryV2(HttpUrl url) throws IOException {
    if (url.encodedPath().equals("/api/v2/services")) {
      return jsonResponse(Codec.JSON.writeStrings(store2.getServiceNames().execute()));
    } else if (url.encodedPath().equals("/api/v2/spans")) {
      String serviceName = url.queryParameter("serviceName");
      return jsonResponse(Codec.JSON.writeStrings(store2.getSpanNames(serviceName).execute()));
    } else if (url.encodedPath().equals("/api/v2/dependencies")) {
      Long endTs = maybeLong(url.queryParameter("endTs"));
      Long lookback = maybeLong(url.queryParameter("lookback"));
      List<DependencyLink> result = store2.getDependencies(
        endTs != null ? endTs : System.currentTimeMillis(),
        lookback != null ? lookback : DEFAULT_LOOKBACK
      ).execute();
      return jsonResponse(Codec.JSON.writeDependencyLinks(result));
    } else if (url.encodedPath().equals("/api/v2/traces")) {
      List<List<zipkin.internal.v2.Span>> traces = store2.getTraces(toQueryRequest2(url)).execute();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      bout.write('[');
      for (int i = 0, length = traces.size(); i < length; ) {
        List<zipkin.internal.v2.Span> trace = traces.get(i);
        writeTrace(bout, trace);
        if (++i < length) bout.write(',');
      }
      bout.write(']');
      return jsonResponse(bout.toByteArray());
    } else if (url.encodedPath().startsWith("/api/v2/trace/")) {
      String traceIdHex = url.encodedPath().replace("/api/v2/trace/", "");
      long traceIdHigh = traceIdHex.length() == 32 ? lowerHexToUnsignedLong(traceIdHex, 0) : 0L;
      long traceIdLow = lowerHexToUnsignedLong(traceIdHex);
      List<zipkin.internal.v2.Span> trace = store2.getTrace(traceIdHigh, traceIdLow).execute();
      if (!trace.isEmpty()) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        writeTrace(bout, trace);
        return jsonResponse(bout.toByteArray());
      }
    }
    return new MockResponse().setResponseCode(404);
  }

  static void writeTrace(ByteArrayOutputStream bout, List<zipkin.internal.v2.Span> trace)
    throws IOException {
    bout.write('[');
    for (int i = 0, length = trace.size(); i < length; ) {
      bout.write(Encoder.JSON.encode(trace.get(i)));
      if (++i < length) bout.write(',');
    }
    bout.write(']');
  }

  MockResponse acceptSpans(RecordedRequest request, SpanDecoder decoder) {
    metrics.incrementMessages();
    byte[] body = request.getBody().readByteArray();
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

    final MockResponse result = new MockResponse();
    consumer.acceptSpans(body, decoder, new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
        result.setResponseCode(202);
      }

      @Override public void onError(Throwable t) {
        String message = t.getMessage();
        result.setBody(message).setResponseCode(message.startsWith("Cannot store") ? 500 : 400);
      }
    });
    return result;
  }

  static QueryRequest toQueryRequest(HttpUrl url) {
    return QueryRequest.builder().serviceName(url.queryParameter("serviceName"))
                                 .spanName(url.queryParameter("spanName"))
                                 .parseAnnotationQuery(url.queryParameter("annotationQuery"))
                                 .minDuration(maybeLong(url.queryParameter("minDuration")))
                                 .maxDuration(maybeLong(url.queryParameter("maxDuration")))
                                 .endTs(maybeLong(url.queryParameter("endTs")))
                                 .lookback(maybeLong(url.queryParameter("lookback")))
                                 .limit(maybeInteger(url.queryParameter("limit"))).build();
  }

  static zipkin.internal.v2.storage.QueryRequest toQueryRequest2(HttpUrl url) {
    Long endTs = maybeLong(url.queryParameter("endTs"));
    Long lookback = maybeLong(url.queryParameter("lookback"));
    Integer limit = maybeInteger(url.queryParameter("limit"));
    return zipkin.internal.v2.storage.QueryRequest.newBuilder()
      .serviceName(url.queryParameter("serviceName"))
      .spanName(url.queryParameter("spanName"))
      .parseAnnotationQuery(url.queryParameter("annotationQuery"))
      .minDuration(maybeLong(url.queryParameter("minDuration")))
      .maxDuration(maybeLong(url.queryParameter("maxDuration")))
      .endTs(endTs != null ? endTs : System.currentTimeMillis())
      .lookback(lookback != null ? lookback : DEFAULT_LOOKBACK)
      .limit(limit != null ? limit : 10).build();
  }

  static @Nullable Long maybeLong(@Nullable String input) {
    return input != null ? Long.valueOf(input) : null;
  }

  static @Nullable Integer maybeInteger(@Nullable String input) {
    return input != null ? Integer.valueOf(input) : null;
  }

  static MockResponse jsonResponse(byte[] content) {
    return new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(new Buffer().write(content));
  }
}
