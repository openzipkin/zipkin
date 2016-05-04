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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zipkin.AsyncSpanConsumer;
import zipkin.Codec;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.Span;
import zipkin.StorageComponent;
import zipkin.internal.SpanConsumerLogger;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.checkNotNull;

/**
 * Implements the POST /api/v1/spans endpoint used by instrumentation.
 */
@RestController
public class ZipkinHttpCollector {
  static final String APPLICATION_THRIFT = "application/x-thrift";

  private final AsyncSpanConsumer consumer;
  private final Codec jsonCodec;
  private final Codec thriftCodec;
  private final SpanConsumerLogger logger;

  /** lazy so transient storage errors don't crash bootstrap */
  @Lazy
  @Autowired ZipkinHttpCollector(StorageComponent storage, CollectorSampler sampler,
      Codec.Factory codecFactory, CollectorMetrics metrics) {
    this.consumer = storage.asyncSpanConsumer(sampler, metrics);
    this.jsonCodec = checkNotNull(codecFactory.get(APPLICATION_JSON_VALUE), APPLICATION_JSON_VALUE);
    this.thriftCodec = checkNotNull(codecFactory.get(APPLICATION_THRIFT), APPLICATION_THRIFT);
    this.logger = new SpanConsumerLogger(ZipkinHttpCollector.class, metrics.forTransport("http"));
  }

  @RequestMapping(value = "/api/v1/spans", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResponseEntity<?> uploadSpansJson(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    logger.acceptedMessage();
    return validateAndStoreSpans(encoding, jsonCodec, body);
  }

  @RequestMapping(value = "/api/v1/spans", method = RequestMethod.POST, consumes = APPLICATION_THRIFT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResponseEntity<?> uploadSpansThrift(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    logger.acceptedMessage();
    return validateAndStoreSpans(encoding, thriftCodec, body);
  }

  ResponseEntity<?> validateAndStoreSpans(String encoding, Codec codec, byte[] body) {
    if (encoding != null && encoding.contains("gzip")) {
      try {
        body = gunzip(body);
      } catch (IOException e) {
        String message = logger.errorReading("Cannot gunzip spans", e);
        return ResponseEntity.badRequest().body(message + "\n"); // newline for prettier curl
      }
    }
    logger.readBytes(body.length);
    List<Span> spans;
    try {
      spans = codec.readSpans(body);
    } catch (RuntimeException e) {
      String message = logger.errorReading(e);
      return ResponseEntity.badRequest().body(message + "\n"); // newline for prettier curl
    }
    if (spans.isEmpty()) return ResponseEntity.accepted().build();
    logger.readSpans(spans.size());
    try {
      consumer.accept(spans, logger.acceptSpansCallback(spans));
    } catch (RuntimeException e) {
      String message = logger.errorAcceptingSpans(spans, e);
      return ResponseEntity.status(500).body(message + "\n"); // newline for prettier curl
    }
    return ResponseEntity.accepted().build();
  }

  private static final ThreadLocal<byte[]> GZIP_BUFFER = new ThreadLocal<byte[]>() {
    @Override protected byte[] initialValue() {
      return new byte[1024];
    }
  };

  static byte[] gunzip(byte[] input) throws IOException {
    Inflater inflater = new Inflater();
    inflater.setInput(input);
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length)) {
      while (!inflater.finished()) {
        int count = inflater.inflate(GZIP_BUFFER.get());
        outputStream.write(GZIP_BUFFER.get(), 0, count);
      }
      return outputStream.toByteArray();
    } catch (DataFormatException e) {
      throw new IOException(e.getMessage(), e);
    }
  }
}
