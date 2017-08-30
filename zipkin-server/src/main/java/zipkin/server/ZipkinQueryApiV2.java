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

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import okio.Buffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.internal.V2StorageComponent;
import zipkin.internal.v2.Call;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.codec.Encoder;
import zipkin.internal.v2.storage.QueryRequest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static zipkin.internal.Util.lowerHexToUnsignedLong;

@RestController
@RequestMapping("/api/v2")
@CrossOrigin("${zipkin.query.allowed-origins:*}")
@ConditionalOnProperty(name = "zipkin.query.enabled", matchIfMissing = true)
@ConditionalOnBean(V2StorageComponent.class)
public class ZipkinQueryApiV2 {

  @Autowired
  @Value("${zipkin.query.lookback:86400000}")
  long defaultLookback = 86400000; // 1 day in millis

  /** The Cache-Control max-age (seconds) for /api/v1/services and /api/v1/spans */
  @Value("${zipkin.query.names-max-age:300}")
  int namesMaxAge = 300; // 5 minutes
  volatile int serviceCount; // used as a threshold to start returning cache-control headers

  private final V2StorageComponent storage;

  @Autowired ZipkinQueryApiV2(V2StorageComponent storage) {
    this.storage = storage; // don't cache spanStore here as it can cause the app to crash!
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(
    @RequestParam(value = "endTs", required = true) long endTs,
    @Nullable @RequestParam(value = "lookback", required = false) Long lookback
  ) throws IOException {
    Call<List<DependencyLink>> call = storage.v2SpanStore()
      .getDependencies(endTs, lookback != null ? lookback : defaultLookback);
    return Codec.JSON.writeDependencyLinks(call.execute());
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getServiceNames() throws IOException {
    List<String> serviceNames = storage.v2SpanStore().getServiceNames().execute();
    serviceCount = serviceNames.size();
    return maybeCacheNames(serviceNames);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  public ResponseEntity<List<String>> getSpanNames(
    @RequestParam(value = "serviceName", required = true) String serviceName
  ) throws IOException {
    return maybeCacheNames(storage.v2SpanStore().getSpanNames(serviceName).execute());
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
    QueryRequest queryRequest = QueryRequest.newBuilder()
      .serviceName(serviceName)
      .spanName(spanName)
      .parseAnnotationQuery(annotationQuery)
      .minDuration(minDuration)
      .maxDuration(maxDuration)
      .endTs(endTs != null ? endTs : System.currentTimeMillis())
      .lookback(lookback != null ? lookback : defaultLookback)
      .limit(limit).build();

    List<List<Span>> traces = storage.v2SpanStore().getTraces(queryRequest).execute();
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    for (int i = 0, iLength = traces.size(); i < iLength; i++) {
      writer.beginArray();
      List<Span> trace = traces.get(i);
      for (int j = 0, jLength = trace.size(); j < jLength; j++) {
        buffer.write(Encoder.JSON.encode(trace.get(j)));
        if (j < jLength) buffer.writeByte(',');
      }
      writer.endArray();
    }
    writer.endArray();
    return buffer.readUtf8();
  }

  @RequestMapping(value = "/trace/{traceIdHex}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public String getTrace(@PathVariable String traceIdHex, WebRequest request) throws IOException {
    long traceIdHigh = traceIdHex.length() == 32 ? lowerHexToUnsignedLong(traceIdHex, 0) : 0L;
    long traceIdLow = lowerHexToUnsignedLong(traceIdHex);
    List<Span> trace = storage.v2SpanStore().getTrace(traceIdHigh, traceIdLow).execute();
    if (trace.isEmpty()) {
      throw new TraceNotFoundException(traceIdHex, traceIdHigh, traceIdLow);
    }
    Buffer buffer = new Buffer();
    JsonWriter writer = JsonWriter.of(buffer);
    writer.beginArray();
    for (int i = 0, length = trace.size(); i < length; i++) {
      buffer.write(Encoder.JSON.encode(trace.get(i)));
      if (i < length) buffer.writeByte(',');
    }
    writer.endArray();
    return buffer.readUtf8();
  }

  @ExceptionHandler(TraceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void notFound() {
  }

  static class TraceNotFoundException extends RuntimeException {
    TraceNotFoundException(String traceIdHex, long traceIdHigh, long traceId) {
      super(String.format("Cannot find trace for id=%s, parsed value=%s", traceIdHex,
        traceIdHigh != 0 ? traceIdHigh + "," + traceId : traceId));
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
