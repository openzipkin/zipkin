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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import zipkin.internal.Nullable;
import zipkin.internal.V2StorageComponent;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.StorageComponent;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/v2")
@ConditionalOnProperty(name = "zipkin.query.enabled", matchIfMissing = true)
public class ZipkinQueryApiV2 {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  final String storageType;
  final StorageComponent storage; // don't cache spanStore here as it can cause the app to crash!
  final long defaultLookback;
  /** The Cache-Control max-age (seconds) for /api/v2/services and /api/v2/spans */
  final int namesMaxAge;

  volatile int serviceCount; // used as a threshold to start returning cache-control headers

  ZipkinQueryApiV2(
    zipkin.storage.StorageComponent storage,
    @Value("${zipkin.storage.type:mem}") String storageType,
    @Value("${zipkin.query.lookback:86400000}") long defaultLookback, // 1 day in millis
    @Value("${zipkin.query.names-max-age:300}") int namesMaxAge // 5 minutes
  ) {
    if (storage instanceof V2StorageComponent) {
      this.storage = ((V2StorageComponent) storage).delegate();
    } else {
      this.storage = null;
    }
    this.storageType = storageType;
    this.defaultLookback = defaultLookback;
    this.namesMaxAge = namesMaxAge;
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(
    @RequestParam(value = "endTs", required = true) long endTs,
    @Nullable @RequestParam(value = "lookback", required = false) Long lookback
  ) throws IOException {
    if (storage == null) throw new Version2StorageNotConfigured();

    Call<List<DependencyLink>> call = storage.spanStore()
      .getDependencies(endTs, lookback != null ? lookback : defaultLookback);
    return DependencyLinkBytesEncoder.JSON_V1.encodeList(call.execute());
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getServiceNames() throws IOException {
    if (storage == null) throw new Version2StorageNotConfigured();

    List<String> serviceNames = storage.spanStore().getServiceNames().execute();
    serviceCount = serviceNames.size();
    return maybeCacheNames(serviceNames);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getSpanNames(
    @RequestParam(value = "serviceName", required = true) String serviceName
  ) throws IOException {
    if (storage == null) throw new Version2StorageNotConfigured();

    return maybeCacheNames(storage.spanStore().getSpanNames(serviceName).execute());
  }

  @RequestMapping(value = "/traces", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public String getTraces(
    @Nullable @RequestParam(value = "serviceName", required = false) String serviceName,
    @Nullable @RequestParam(value = "spanName", required = false) String spanName,
    @Nullable @RequestParam(value = "annotationQuery", required = false) String annotationQuery,
    @Nullable @RequestParam(value = "minDuration", required = false) Long minDuration,
    @Nullable @RequestParam(value = "maxDuration", required = false) Long maxDuration,
    @Nullable @RequestParam(value = "endTs", required = false) Long endTs,
    @Nullable @RequestParam(value = "lookback", required = false) Long lookback,
    @RequestParam(value = "limit", defaultValue = "10") int limit
  ) throws IOException {
    if (storage == null) throw new Version2StorageNotConfigured();

    QueryRequest queryRequest = QueryRequest.newBuilder()
      .serviceName(serviceName)
      .spanName(spanName)
      .parseAnnotationQuery(annotationQuery)
      .minDuration(minDuration)
      .maxDuration(maxDuration)
      .endTs(endTs != null ? endTs : System.currentTimeMillis())
      .lookback(lookback != null ? lookback : defaultLookback)
      .limit(limit).build();

    List<List<Span>> traces = storage.spanStore().getTraces(queryRequest).execute();
    return new String(writeTraces(SpanBytesEncoder.JSON_V2, traces), UTF_8);
  }

  @RequestMapping(value = "/trace/{traceIdHex}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public String getTrace(@PathVariable String traceIdHex, WebRequest request) throws IOException {
    if (storage == null) throw new Version2StorageNotConfigured();

    List<Span> trace = storage.spanStore().getTrace(traceIdHex).execute();
    if (trace.isEmpty()) throw new TraceNotFoundException(traceIdHex);
    return new String(SpanBytesEncoder.JSON_V2.encodeList(trace), UTF_8);
  }

  @ExceptionHandler(Version2StorageNotConfigured.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void version2StorageNotConfigured() {
  }

  /** {@linkplain V2StorageComponent} is still an internal, so we can't hard-wire based on it. */
  class Version2StorageNotConfigured extends RuntimeException {
    Version2StorageNotConfigured() {
      super("Api version 2 not yet supported for " + storageType);
    }
  }

  @ExceptionHandler(TraceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void notFound() {
  }

  static class TraceNotFoundException extends RuntimeException {
    TraceNotFoundException(String traceIdHex) {
      super("Cannot find trace " + traceIdHex);
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

  // This is inlined here as there isn't enough re-use to warrant it being in the zipkin2 library
  static byte[] writeTraces(SpanBytesEncoder codec, List<List<zipkin2.Span>> traces) {
    // Get the encoded size of the nested list so that we don't need to grow the buffer
    int length = traces.size();
    int sizeInBytes = 2; // []
    if (length > 1) sizeInBytes += length - 1; // comma to join elements

    for (int i = 0; i < length; i++) {
      List<zipkin2.Span> spans = traces.get(i);
      int jLength = spans.size();
      sizeInBytes += 2; // []
      if (jLength > 1) sizeInBytes += jLength - 1; // comma to join elements
      for (int j = 0; j < jLength; j++) {
        sizeInBytes += codec.sizeInBytes(spans.get(j));
      }
    }

    byte[] out = new byte[sizeInBytes];
    int pos = 0;
    out[pos++] = '['; // start list of traces
    for (int i = 0; i < length; i++) {
      pos += codec.encodeList(traces.get(i), out, pos);
      if (i + 1 < length) out[pos++] = ',';
    }
    out[pos] = ']'; // stop list of traces
    return out;
  }
}
