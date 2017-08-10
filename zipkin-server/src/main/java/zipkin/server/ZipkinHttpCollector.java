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
package zipkin.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zipkin.Span;
import zipkin.SpanDecoder;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.Nullable;
import zipkin.internal.Span2JsonDecoder;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Implements the POST /api/v1/spans endpoint used by instrumentation.
 */
@RestController
@CrossOrigin("${zipkin.query.allowed-origins:*}")
@ConditionalOnProperty(name = "zipkin.collector.http.enabled", matchIfMissing = true)
public class ZipkinHttpCollector {
  static final ResponseEntity<?> SUCCESS = ResponseEntity.accepted().build();
  static final String APPLICATION_THRIFT = "application/x-thrift";
  static final SpanDecoder JSON2_DECODER = new Span2JsonDecoder();

  final CollectorMetrics metrics;
  final Collector collector;

  @Autowired ZipkinHttpCollector(StorageComponent storage, CollectorSampler sampler,
      CollectorMetrics metrics) {
    this.metrics = metrics.forTransport("http");
    this.collector = Collector.builder(getClass())
        .storage(storage).sampler(sampler).metrics(this.metrics).build();
  }

  @RequestMapping(value = "/api/v2/spans", method = POST)
  public ListenableFuture<ResponseEntity<?>> uploadSpansJson2(
    @RequestHeader(value = "Content-Encoding", required = false) String encoding,
    @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, JSON2_DECODER, body);
  }

  @RequestMapping(value = "/api/v1/spans", method = POST)
  public ListenableFuture<ResponseEntity<?>> uploadSpansJson(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, SpanDecoder.JSON_DECODER, body);
  }

  @RequestMapping(value = "/api/v1/spans", method = POST, consumes = APPLICATION_THRIFT)
  public ListenableFuture<ResponseEntity<?>> uploadSpansThrift(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, SpanDecoder.THRIFT_DECODER, body);
  }

  ListenableFuture<ResponseEntity<?>> validateAndStoreSpans(String encoding, SpanDecoder decoder,
      byte[] body) {
    SettableListenableFuture<ResponseEntity<?>> result = new SettableListenableFuture<>();
    metrics.incrementMessages();
    if (encoding != null && encoding.contains("gzip")) {
      try {
        body = gunzip(body);
      } catch (IOException e) {
        metrics.incrementMessagesDropped();
        result.set(ResponseEntity.badRequest().body("Cannot gunzip spans: " + e.getMessage() + "\n"));
      }
    }
    collector.acceptSpans(body, decoder, new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
        result.set(SUCCESS);
      }

      @Override public void onError(Throwable t) {
        String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        result.set(t.getMessage() == null || message.startsWith("Cannot store")
            ? ResponseEntity.status(500).body(message + "\n")
            : ResponseEntity.status(400).body(message + "\n"));
      }
    });
    return result;
  }

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
