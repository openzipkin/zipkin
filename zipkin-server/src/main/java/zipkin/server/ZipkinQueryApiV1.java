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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.Buffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import zipkin.Codec;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.SpanStore;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.gunzip;

/**
 * Implements the json api used by {@code zipkin-web}.
 *
 * See com.twitter.zipkin.query.ZipkinQueryController
 */
@RestController
@RequestMapping("/api/v1")
public class ZipkinQueryApiV1 {
  private static final Logger LOGGER = Logger.getLogger(ZipkinQueryApiV1.class.getName());
  private static final String APPLICATION_THRIFT = "application/x-thrift";

  @Autowired
  @Value("${zipkin.query.lookback:86400000}")
  int defaultLookback = 86400000; // 7 days in millis

  private final SpanStore spanStore;
  private final ZipkinSpanWriter spanWriter;
  private final Codec jsonCodec;
  private final Codec thriftCodec;

  @Autowired
  public ZipkinQueryApiV1(SpanStore spanStore, ZipkinSpanWriter spanWriter, Codec.Factory codecFactory) {
    this.spanStore = spanStore;
    this.spanWriter = spanWriter;
    this.jsonCodec = checkNotNull(codecFactory.get(APPLICATION_JSON_VALUE), APPLICATION_JSON_VALUE);
    this.thriftCodec = checkNotNull(codecFactory.get(APPLICATION_THRIFT), APPLICATION_THRIFT);
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(@RequestParam(value = "endTs", required = true) long endTs,
                                @RequestParam(value = "lookback", required = false) Long lookback) {
    return jsonCodec.writeDependencyLinks(spanStore.getDependencies(endTs, lookback != null ? lookback : defaultLookback));
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  public List<String> getServiceNames() {
    return spanStore.getServiceNames();
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  public List<String> getSpanNames(
      @RequestParam(value = "serviceName", required = true) String serviceName) {
    return spanStore.getSpanNames(serviceName);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResponseEntity<?> uploadSpansJson(
      @RequestHeader(value = "Content-Encoding", required = false) String encoding,
      @RequestBody byte[] body
  ) {
    return validateAndStoreSpans(encoding, jsonCodec, body);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.POST, consumes = APPLICATION_THRIFT)
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
    spanWriter.write(spanStore, spans);
    return ResponseEntity.accepted().build();
  }

  @RequestMapping(value = "/traces", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getTraces(
      @RequestParam(value = "serviceName", required = true) String serviceName,
      @RequestParam(value = "spanName", defaultValue = "all") String spanName,
      @RequestParam(value = "annotationQuery", required = false) String annotationQuery,
      @RequestParam(value = "minDuration", required = false) Long minDuration,
      @RequestParam(value = "maxDuration", required = false) Long maxDuration,
      @RequestParam(value = "endTs", required = false) Long endTs,
      @RequestParam(value = "lookback", required = false) Long lookback,
      @RequestParam(value = "limit", required = false) Integer limit) {
    QueryRequest queryRequest = new QueryRequest.Builder(serviceName)
        .spanName(spanName)
        .parseAnnotationQuery(annotationQuery)
        .minDuration(minDuration)
        .maxDuration(maxDuration)
        .endTs(endTs)
        .lookback(lookback != null ? lookback : defaultLookback)
        .limit(limit).build();

    return jsonCodec.writeTraces(spanStore.getTraces(queryRequest));
  }

  @RequestMapping(value = "/trace/{traceId}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getTrace(@PathVariable String traceId) {
    @SuppressWarnings("resource")
    long id = new Buffer().writeUtf8(traceId).readHexadecimalUnsignedLong();
    List<List<Span>> traces = spanStore.getTracesByIds(Collections.singletonList(id));

    if (traces.isEmpty()) {
      throw new TraceNotFoundException(traceId, id);
    }
    return jsonCodec.writeSpans(traces.get(0));
  }

  @ExceptionHandler(TraceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void notFound() {
  }

  static class TraceNotFoundException extends RuntimeException {
    public TraceNotFoundException(String traceId, long id) {
      super("Cannot find trace for id=" + traceId + ", long value=" + id);
    }
  }
}
