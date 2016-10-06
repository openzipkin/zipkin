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
package zipkin.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import zipkin.Codec;
import zipkin.collector.Collector;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.Nullable;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Implements the POST /api/v1/spans endpoint used by instrumentation.
 */
@RestController
@CrossOrigin("${zipkin.query.allowed-origins:*}")
public class ZipkinHttpCollector {
  static final ResponseEntity<?> SUCCESS = ResponseEntity.accepted().build();
  static final String APPLICATION_THRIFT = "application/x-thrift";

  final CollectorMetrics metrics;
  final Collector collector;

  @Autowired ZipkinHttpCollector(StorageComponent storage, CollectorSampler sampler,
      CollectorMetrics metrics) {
    this.metrics = metrics.forTransport("http");
    this.collector = Collector.builder(getClass())
        .storage(storage).sampler(sampler).metrics(this.metrics).build();
  }

  @RequestMapping(value = "/api/v1/spans", method = POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DeferredResult<ResponseEntity<?>> uploadSpansJson(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, Codec.JSON, body);
  }

  @RequestMapping(value = "/api/v1/spans", method = POST, consumes = APPLICATION_THRIFT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DeferredResult<ResponseEntity<?>> uploadSpansThrift(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, Codec.THRIFT, body);
  }

  DeferredResult<ResponseEntity<?>> validateAndStoreSpans(String encoding, Codec codec,
      byte[] body) {
    DeferredResult<ResponseEntity<?>> result = new DeferredResult<>();
    metrics.incrementMessages();
    if (encoding != null && encoding.contains("gzip")) {
      try {
        body = gunzip(body);
      } catch (IOException e) {
        metrics.incrementMessagesDropped();
        result.setResult(
            ResponseEntity.badRequest().body("Cannot gunzip spans: " + e.getMessage() + "\n"));
        return result;
      }
    }
    collector.acceptSpans(body, codec, new Callback<Void>() {
      @Override public void onSuccess(@Nullable Void value) {
        result.setResult(SUCCESS);
      }

      @Override public void onError(Throwable t) {
        String message = t.getMessage();
        result.setErrorResult(message.startsWith("Cannot store")
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
