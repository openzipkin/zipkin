/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.internal.Buffer;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.StorageComponent;

@ConditionalOnProperty(name = "zipkin.query.enabled", matchIfMissing = true)
public class ZipkinQueryApiV2 {
  final String storageType;
  final StorageComponent storage; // don't cache spanStore here as it can cause the app to crash!
  final long defaultLookback;
  /** The Cache-Control max-age (seconds) for /api/v2/services and /api/v2/spans */
  final int namesMaxAge;
  final List<String> autocompleteKeys;

  volatile int serviceCount; // used as a threshold to start returning cache-control headers

  ZipkinQueryApiV2(
    StorageComponent storage,
    @Value("${zipkin.storage.type:mem}") String storageType,
    @Value("${zipkin.query.lookback:86400000}") long defaultLookback, // 1 day in millis
    @Value("${zipkin.query.names-max-age:300}") int namesMaxAge, // 5 minutes
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys
  ) {
    this.storage = storage;
    this.storageType = storageType;
    this.defaultLookback = defaultLookback;
    this.namesMaxAge = namesMaxAge;
    this.autocompleteKeys = autocompleteKeys;
  }

  @Get("/api/v2/dependencies")
  public HttpResponse getDependencies(
    @Param("endTs") long endTs,
    @Nullable @Param("lookback") Long lookback) throws IOException {
    Call<List<DependencyLink>> call =
      storage.spanStore().getDependencies(endTs, lookback != null ? lookback : defaultLookback);
    return jsonResponse(DependencyLinkBytesEncoder.JSON_V1.encodeList(call.execute()));
  }

  @Get("/api/v2/services")
  public HttpResponse getServiceNames() throws IOException {
    List<String> serviceNames = storage.spanStore().getServiceNames().execute();
    serviceCount = serviceNames.size();
    return maybeCacheNames(serviceNames);
  }

  @Get("/api/v2/spans")
  public HttpResponse getSpanNames(@Param("serviceName") String serviceName) throws IOException {
    return maybeCacheNames(storage.spanStore().getSpanNames(serviceName).execute());
  }

  @Get("/api/v2/traces")
  public HttpResponse getTraces(
    @Nullable @Param("serviceName") String serviceName,
    @Nullable @Param("spanName") String spanName,
    @Nullable @Param("annotationQuery") String annotationQuery,
    @Nullable @Param("minDuration") Long minDuration,
    @Nullable @Param("maxDuration") Long maxDuration,
    @Nullable @Param("endTs") Long endTs,
    @Nullable @Param("lookback") Long lookback,
    @Default("10") @Param("limit") int limit)
    throws IOException {
    QueryRequest queryRequest =
      QueryRequest.newBuilder()
        .serviceName(serviceName)
        .spanName(spanName)
        .parseAnnotationQuery(annotationQuery)
        .minDuration(minDuration)
        .maxDuration(maxDuration)
        .endTs(endTs != null ? endTs : System.currentTimeMillis())
        .lookback(lookback != null ? lookback : defaultLookback)
        .limit(limit)
        .build();

    List<List<Span>> traces = storage.spanStore().getTraces(queryRequest).execute();
    return jsonResponse(writeTraces(SpanBytesEncoder.JSON_V2, traces));
  }

  @Get("/api/v2/trace/{traceIdHex}")
  @ExceptionHandler(NotFoundHandler.class)
  public HttpResponse getTrace(@Param("traceIdHex") String traceIdHex) throws IOException {
    List<Span> trace = storage.spanStore().getTrace(traceIdHex).execute();
    if (trace.isEmpty()) throw new NoSuchElementException(traceIdHex);
    return jsonResponse(SpanBytesEncoder.JSON_V2.encodeList(trace));
  }

  static final class NotFoundHandler implements ExceptionHandlerFunction {
    @Override
    public HttpResponse handleException(RequestContext ctx, HttpRequest req, Throwable cause) {
      if (cause instanceof NoSuchElementException) {
        return HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.PLAIN_TEXT_UTF_8,
          cause.getMessage() + " not found");
      }
      // To the next exception handler.
      return ExceptionHandlerFunction.fallthrough();
    }
  }

  static HttpResponse jsonResponse(byte[] body) {
    return HttpResponse.of(HttpHeaders.of(200)
      .contentType(MediaType.JSON)
      .setInt(HttpHeaderNames.CONTENT_LENGTH, body.length), HttpData.of(body));
  }

  static final Buffer.Writer<String> QUOTED_STRING_WRITER = new Buffer.Writer<String>() {
    @Override public int sizeInBytes(String value) {
      return Buffer.utf8SizeInBytes(value) + 2; // quotes
    }

    @Override public void write(String value, Buffer buffer) {
      buffer.writeByte('"').writeUtf8(value).writeByte('"');
    }
  };

  @Get("/api/v2/trace/autocompleteKeys")
  public HttpResponse getAutocompleteKeys() {
    return maybeCacheNames(autocompleteKeys);
  }

  @Get("/api/v2/trace/autocompleteKeys")
  public HttpResponse getAutocompleteValues(@Param("key") String key) throws IOException {
    return maybeCacheNames(storage.autocompleteTags().getValues(key).execute());
  }

  /**
   * We cache names if there are more than 3 names. This helps people getting started: if we cache
   * empty results, users have more questions. We assume caching becomes a concern when zipkin is in
   * active use, and active use usually implies more than 3 services.
   */
  HttpResponse maybeCacheNames(List<String> values) {
    byte[] body = JsonCodec.writeList(QUOTED_STRING_WRITER, values);
    HttpHeaders headers = HttpHeaders.of(200)
      .contentType(MediaType.JSON)
      .setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
    if (serviceCount > 3) {
      headers = headers.add(
        HttpHeaderNames.CACHE_CONTROL,
        CacheControl.maxAge(namesMaxAge, TimeUnit.SECONDS).mustRevalidate().getHeaderValue()
      );
    }
    return HttpResponse.of(headers, HttpData.of(body));
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
