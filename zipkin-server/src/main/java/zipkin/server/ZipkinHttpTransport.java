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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zipkin.Codec;
import zipkin.Span;
import zipkin.AsyncSpanConsumer;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.gunzip;

/**
 * Implements the POST /api/v1/spans endpoint used by instrumentation.
 */
@RestController
public class ZipkinHttpTransport {
  static final Logger LOGGER = Logger.getLogger(ZipkinHttpTransport.class.getName());
  static final String APPLICATION_THRIFT = "application/x-thrift";

  private final AsyncSpanConsumer spanConsumer;
  private final Codec jsonCodec;
  private final Codec thriftCodec;

  @Autowired
  public ZipkinHttpTransport(AsyncSpanConsumer spanConsumer, Codec.Factory codecFactory) {
    this.spanConsumer = spanConsumer;
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

  private ResponseEntity<?> validateAndStoreSpans(String encoding, Codec codec, byte[] body) {
    if (encoding != null && encoding.contains("gzip")) {
      try {
        body = gunzip(body);
      } catch (IOException e) {
        String message = e.getMessage();
        if (message == null) message = "Error gunzipping spans";
        return ResponseEntity.badRequest().body(message);
      }
    }
    List<Span> spans;
    try {
      spans = codec.readSpans(body);
    } catch (IllegalArgumentException e) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.log(Level.FINE, e.getMessage(), e);
      }
      return ResponseEntity.badRequest().body(e.getMessage() + "\n"); // newline for prettier curl
    }
    spanConsumer.accept(spans, AsyncSpanConsumer.NOOP_CALLBACK);
    return ResponseEntity.accepted().build();
  }
}
