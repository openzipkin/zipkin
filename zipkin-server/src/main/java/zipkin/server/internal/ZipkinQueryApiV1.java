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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import zipkin.Codec;
import zipkin.Span;
import zipkin.storage.QueryRequest;
import zipkin.storage.StorageComponent;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.lowerHexToUnsignedLong;

/**
 * Implements the json api used by the Zipkin UI
 *
 * See com.twitter.zipkin.query.ZipkinQueryController
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "zipkin.query.enabled", matchIfMissing = true)
public class ZipkinQueryApiV1 {

  @Autowired
  @Value("${zipkin.query.lookback:86400000}")
  int defaultLookback = 86400000; // 1 day in millis

  /** The Cache-Control max-age (seconds) for /api/v1/services and /api/v1/spans */
  @Value("${zipkin.query.names-max-age:300}")
  int namesMaxAge = 300; // 5 minutes
  volatile int serviceCount; // used as a threshold to start returning cache-control headers

  private final StorageComponent storage;

  @Autowired
  public ZipkinQueryApiV1(StorageComponent storage) {
    this.storage = storage; // don't cache spanStore here as it can cause the app to crash!
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(@RequestParam(value = "endTs", required = true) long endTs,
                                @RequestParam(value = "lookback", required = false) Long lookback) {
    return Codec.JSON.writeDependencyLinks(storage.spanStore().getDependencies(endTs, lookback != null ? lookback : defaultLookback));
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getServiceNames() {
    List<String> serviceNames = storage.spanStore().getServiceNames();
    serviceCount = serviceNames.size();
    return maybeCacheNames(serviceNames);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getSpanNames(
      @RequestParam(value = "serviceName", required = true) String serviceName) {
    return maybeCacheNames(storage.spanStore().getSpanNames(serviceName));
  }

  @RequestMapping(value = "/traces", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public String getTraces(
      @RequestParam(value = "serviceName", required = false) String serviceName,
      @RequestParam(value = "spanName", defaultValue = "all") String spanName,
      @RequestParam(value = "annotationQuery", required = false) String annotationQuery,
      @RequestParam(value = "minDuration", required = false) Long minDuration,
      @RequestParam(value = "maxDuration", required = false) Long maxDuration,
      @RequestParam(value = "endTs", required = false) Long endTs,
      @RequestParam(value = "lookback", required = false) Long lookback,
      @RequestParam(value = "limit", required = false) Integer limit) {
    QueryRequest queryRequest = QueryRequest.builder()
        .serviceName(serviceName)
        .spanName(spanName)
        .parseAnnotationQuery(annotationQuery)
        .minDuration(minDuration)
        .maxDuration(maxDuration)
        .endTs(endTs)
        .lookback(lookback != null ? lookback : defaultLookback)
        .limit(limit).build();

    return new String(Codec.JSON.writeTraces(storage.spanStore().getTraces(queryRequest)), UTF_8);
  }

  @RequestMapping(value = "/trace/{traceIdHex}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public String getTrace(@PathVariable String traceIdHex, WebRequest request) {
    long traceIdHigh = traceIdHex.length() == 32 ? lowerHexToUnsignedLong(traceIdHex, 0) : 0L;
    long traceIdLow = lowerHexToUnsignedLong(traceIdHex);
    String[] raw = request.getParameterValues("raw"); // RequestParam doesn't work for param w/o value
    List<Span> trace = raw != null
        ? storage.spanStore().getRawTrace(traceIdHigh, traceIdLow)
        : storage.spanStore().getTrace(traceIdHigh, traceIdLow);
    if (trace == null) {
      throw new TraceNotFoundException(traceIdHex, traceIdHigh, traceIdLow);
    }
    return new String(Codec.JSON.writeSpans(trace), UTF_8);
  }

  @ExceptionHandler(TraceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void notFound() {
  }

  static class TraceNotFoundException extends RuntimeException {
    public TraceNotFoundException(String traceIdHex, Long traceIdHigh, long traceId) {
      super(String.format("Cannot find trace for id=%s,  parsed value=%s", traceIdHex,
          traceIdHigh != null ? traceIdHigh + "," + traceId : traceId));
    }
  }

  /**
   * We cache names if there are more than 3 services. This helps people getting started: if we
   * cache empty results, users have more questions. We assume caching becomes a concern when zipkin
   * is in active use, and active use usually implies more than 3 services.
   */
  ResponseEntity<List<String>> maybeCacheNames(List<String> names) {
    ResponseEntity.BodyBuilder response = ResponseEntity.ok();
    if (serviceCount > 3) {
      response.cacheControl(CacheControl.maxAge(namesMaxAge, TimeUnit.SECONDS).mustRevalidate());
    }
    return response.body(names);
  }
}
