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

import java.io.IOException;
import java.util.List;
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
import zipkin.Sampler;
import zipkin.Span;
import zipkin.StorageComponent;
import zipkin.internal.SpanConsumerLogger;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.gunzip;

/**
 * Implements the POST /api/v1/spans endpoint used by instrumentation.
 */
@RestController
public class ZipkinHttpTransport {
  static final String APPLICATION_THRIFT = "application/x-thrift";

  private final SpanConsumerLogger logger = new SpanConsumerLogger(ZipkinHttpTransport.class);
  private final AsyncSpanConsumer consumer;
  private final Codec jsonCodec;
  private final Codec thriftCodec;

  /** lazy so transient storage errors don't crash bootstrap */
  @Lazy
  @Autowired
  ZipkinHttpTransport(StorageComponent storage, Sampler sampler, Codec.Factory codecFactory) {
    this.consumer = storage.asyncSpanConsumer(sampler);
    this.jsonCodec = checkNotNull(codecFactory.get(APPLICATION_JSON_VALUE), APPLICATION_JSON_VALUE);
    this.thriftCodec = checkNotNull(codecFactory.get(APPLICATION_THRIFT), APPLICATION_THRIFT);
  }

  @RequestMapping(value = "/api/v1/spans", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResponseEntity<?> uploadSpansJson(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, jsonCodec, body);
  }

  @RequestMapping(value = "/api/v1/spans", method = RequestMethod.POST, consumes = APPLICATION_THRIFT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResponseEntity<?> uploadSpansThrift(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, thriftCodec, body);
  }

  ResponseEntity<?> validateAndStoreSpans(String encoding, Codec codec, byte[] body) {
    if (encoding != null && encoding.contains("gzip")) {
      try {
        body = gunzip(body);
      } catch (IOException e) {
        String message = logger.error("Cannot gunzip spans", e);
        return ResponseEntity.badRequest().body(message + "\n"); // newline for prettier curl
      }
    }
    List<Span> spans;
    try {
      spans = codec.readSpans(body);
    } catch (RuntimeException e) {
      String message = logger.errorDecoding(e);
      return ResponseEntity.badRequest().body(message + "\n"); // newline for prettier curl
    }
    if (spans.isEmpty()) return ResponseEntity.accepted().build();
    try {
      consumer.accept(spans, logger.acceptSpansCallback(spans));
    } catch (RuntimeException e) {
      String message = logger.errorAcceptingSpans(spans, e);
      return ResponseEntity.status(500).body(message + "\n"); // newline for prettier curl
    }
    return ResponseEntity.accepted().build();
  }
}
