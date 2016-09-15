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
import java.util.List;
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
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static zipkin.internal.Util.lowerHexToUnsignedLong;

final class ZipkinDispatcher extends Dispatcher {
  private final SpanStore store;
  private final Collector consumer;
  private final CollectorMetrics metrics;
  private final MockWebServer server;

  ZipkinDispatcher(StorageComponent storage, CollectorMetrics metrics, MockWebServer server) {
    this.store = storage.spanStore();
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
      } else if (url.encodedPath().equals("/api/v1/services")) {
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
        String traceId = url.encodedPath().replace("/api/v1/trace/", "");
        long id = lowerHexToUnsignedLong(traceId);
        List<Span> trace = url.queryParameterNames().contains("raw")
            ? store.getRawTrace(id) : store.getTrace(id);
        if (trace != null) return jsonResponse(Codec.JSON.writeSpans(trace));
      }
    } else if (request.getMethod().equals("POST")) {
      if (url.encodedPath().equals("/api/v1/spans")) {
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
        String type = request.getHeader("Content-Type");
        Codec codec = type != null && type.contains("/x-thrift") ? Codec.THRIFT : Codec.JSON;

        final MockResponse result = new MockResponse();
        consumer.acceptSpans(body, codec, new Callback<Void>() {
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
    } else { // unsupported method
      return new MockResponse().setResponseCode(405);
    }
    return new MockResponse().setResponseCode(404);
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

  static Long maybeLong(String input) {
    return input != null ? Long.valueOf(input) : null;
  }

  static Integer maybeInteger(String input) {
    return input != null ? Integer.valueOf(input) : null;
  }

  static MockResponse jsonResponse(byte[] content) {
    return new MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody(new Buffer().write(content));
  }
}
