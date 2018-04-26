/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server.internal;

import io.undertow.io.Receiver;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import zipkin.SpanDecoder;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.V2JsonSpanDecoder;
import zipkin.internal.V2Proto3SpanDecoder;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static zipkin.SpanDecoder.JSON_DECODER;
import static zipkin.SpanDecoder.THRIFT_DECODER;

/**
 * Implements the POST /api/v1/spans and /api/v2/spans endpoints used by instrumentation.
 */
@Configuration
@ConditionalOnProperty(name = "zipkin.collector.http.enabled", matchIfMissing = true)
class ZipkinHttpCollector implements HttpHandler, HandlerWrapper {

  static final HttpString
    POST = HttpString.tryFromString("POST"),
    CONTENT_TYPE = HttpString.tryFromString("Content-Type"),
    CONTENT_ENCODING = HttpString.tryFromString("Content-Encoding");

  final CollectorMetrics metrics;
  final Collector collector;
  final HttpCollector JSON_V2, PROTO3, JSON_V1, THRIFT;
  final Receiver.ErrorCallback errorCallback;
  private HttpHandler next;

  @Autowired ZipkinHttpCollector(StorageComponent storage, CollectorSampler sampler,
    CollectorMetrics metrics) {
    this.metrics = metrics.forTransport("http");
    this.collector = Collector.builder(getClass())
      .storage(storage).sampler(sampler).metrics(this.metrics).build();
    this.JSON_V2 = new HttpCollector(new V2JsonSpanDecoder());
    this.PROTO3 = new HttpCollector(new V2Proto3SpanDecoder());
    this.JSON_V1 = new HttpCollector(JSON_DECODER);
    this.THRIFT = new HttpCollector(THRIFT_DECODER);
    this.errorCallback = new Receiver.ErrorCallback() {
      @Override public void error(HttpServerExchange exchange, IOException e) {
        ZipkinHttpCollector.this.metrics.incrementMessagesDropped();
        ZipkinHttpCollector.error(exchange, e);
      }
    };
  }

  @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
    boolean v2 = exchange.getRelativePath().equals("/api/v2/spans");
    boolean v1 = !v2 && exchange.getRelativePath().equals("/api/v1/spans");
    if (!v2 && !v1) {
      next.handleRequest(exchange);
      return;
    }

    if (!POST.equals(exchange.getRequestMethod())) {
      next.handleRequest(exchange);
      return;
    }

    String contentTypeValue = exchange.getRequestHeaders().getFirst(CONTENT_TYPE);
    boolean json = contentTypeValue == null || contentTypeValue.startsWith("application/json");
    boolean thrift = !json && contentTypeValue.startsWith("application/x-thrift");
    boolean proto = v2 && !json && contentTypeValue.startsWith("application/x-protobuf");
    if (!json && !thrift && !proto) {
      exchange.setStatusCode(400)
        .getResponseSender()
        .send("unsupported content type " + contentTypeValue + "\n");
      return;
    }

    HttpCollector collector = v2 ? (json ? JSON_V2 : PROTO3) : thrift ? THRIFT : JSON_V1;
    metrics.incrementMessages();
    exchange.getRequestReceiver().receiveFullBytes(collector, errorCallback);
  }

  @Override public HttpHandler wrap(HttpHandler handler) {
    this.next = handler;
    return this;
  }

  final class HttpCollector implements Receiver.FullBytesCallback {
    final SpanDecoder decoder;

    HttpCollector(SpanDecoder decoder) {
      this.decoder = decoder;
    }

    @Override public void handle(HttpServerExchange exchange, byte[] body) {
      String encoding = exchange.getRequestHeaders().getFirst(CONTENT_ENCODING);

      if (encoding != null && encoding.contains("gzip")) {
        try {
          body = gunzip(body);
        } catch (IOException e) {
          metrics.incrementMessagesDropped();
          exchange.setStatusCode(400)
            .getResponseSender().send("Cannot gunzip spans: " + e.getMessage() + "\n");
          return;
        }
      }
      collector.acceptSpans(body, decoder, new Callback<Void>() {
        @Override public void onSuccess(Void value) {
          exchange.setStatusCode(202).getResponseSender().close();
        }

        @Override public void onError(Throwable t) {
          error(exchange, t);
        }
      });
    }
  }

  static void error(HttpServerExchange exchange, Throwable e) {
    String message = e.getMessage();
    int code = message == null || message.startsWith("Cannot store") ? 500 : 400;
    if (message == null) message = e.getClass().getSimpleName();
    exchange.setStatusCode(code).getResponseSender().send(message);
  }

  // TODO: there's gotta be an N/IO way to gunzip
  private static final ThreadLocal<byte[]> GZIP_BUFFER = new ThreadLocal<byte[]>() {
    @Override protected byte[] initialValue() {
      return new byte[1024];
    }
  };

  static byte[] gunzip(byte[] input) throws IOException {
    GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(input));
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length)) {
      byte[] buf = GZIP_BUFFER.get();
      int len;
      while ((len = in.read(buf)) > 0) {
        outputStream.write(buf, 0, len);
      }
      return outputStream.toByteArray();
    }
  }
}
