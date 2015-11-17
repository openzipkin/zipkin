/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.server;

import io.zipkin.Codec;
import io.zipkin.DependencyLink;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.Util.Serializer;
import java.util.Collections;
import java.util.List;
import okio.Buffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static io.zipkin.internal.Util.writeJsonList;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Implements the json api used by {@code zipkin-web}.
 *
 * See com.twitter.zipkin.query.ZipkinQueryController
 */
@RestController
@RequestMapping("/api/v1")
public class ZipkinQueryApiV1 {

  static final Serializer<List<List<Span>>> TRACES_TO_JSON = writeJsonList(Codec.JSON::writeSpans);
  static final Serializer<List<DependencyLink>> DEPENDENCY_LINKS_TO_JSON = writeJsonList(Codec.JSON::writeDependencyLink);
  private static final String THRIFT = "application/x-thrift";

  private SpanStore spanStore;
  private ZipkinSpanWriter spanWriter;
  private final static String DEFAULT_LOOKBACK = "86400000"; // 7 days in millis

  @Autowired
  public ZipkinQueryApiV1(SpanStore spanStore, ZipkinSpanWriter spanWriter) {
    this.spanStore = spanStore;
    this.spanWriter = spanWriter;
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(@RequestParam(value = "endTs", required = true) long endTs,
                                @RequestParam(value = "lookback", required = false, defaultValue = DEFAULT_LOOKBACK) long lookback) {
    return DEPENDENCY_LINKS_TO_JSON.apply(this.spanStore.getDependencies(endTs, lookback));
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  public List<String> getServiceNames() {
    return this.spanStore.getServiceNames();
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  public List<String> getSpanNames(
      @RequestParam(value = "serviceName", required = true) String serviceName) {
    return this.spanStore.getSpanNames(serviceName);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void uploadSpansJson(@RequestBody byte[] body) {
    List<Span> spans = Codec.JSON.readSpans(body);
    if (spans == null) throw new MalformedSpansException(APPLICATION_JSON_VALUE);
    this.spanWriter.write(this.spanStore, spans);
  }

  @RequestMapping(value = "/spans", method = RequestMethod.POST, consumes = THRIFT)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void uploadSpansThrift(@RequestBody byte[] body) {
    List<Span> spans = Codec.THRIFT.readSpans(body);
    if (spans == null) throw new MalformedSpansException(THRIFT);
    this.spanWriter.write(this.spanStore, spans);
  }

  @RequestMapping(value = "/traces", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getTraces(
      @RequestParam(value = "serviceName", required = true) String serviceName,
      @RequestParam(value = "spanName", defaultValue = "all") String spanName,
      @RequestParam(value = "annotationQuery", required = false) String annotationQuery,
      @RequestParam(value = "minDuration", required = false) Long minDuration,
      @RequestParam(value = "maxDuration", required = false) Long maxDuration,
      @RequestParam(value = "endTs", required = false) Long endTs,
      @RequestParam(value = "lookback", required = false, defaultValue = DEFAULT_LOOKBACK) long lookback,
      @RequestParam(value = "limit", required = false) Integer limit) {
    QueryRequest.Builder builder = new QueryRequest.Builder()
        .serviceName(serviceName)
        .spanName(spanName.equals("all") ? null : spanName)
        .minDuration(minDuration)
        .maxDuration(maxDuration)
        .endTs(endTs)
        .lookback(lookback)
        .limit(limit);

    if (annotationQuery != null && !annotationQuery.isEmpty()) {
      for (String ann : annotationQuery.split(" and ")) {
        if (ann.indexOf('=') == -1) {
          builder.addAnnotation(ann);
        } else {
          String[] keyValue = ann.split("=");
          if (keyValue.length < 2 || keyValue[1] == null) {
            builder.addAnnotation(ann);
          }
          builder.addBinaryAnnotation(keyValue[0], keyValue[1]);
        }
      }
    }
    return TRACES_TO_JSON.apply(this.spanStore.getTraces(builder.build()));
  }

  @RequestMapping(value = "/trace/{traceId}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getTrace(@PathVariable String traceId) {
    @SuppressWarnings("resource")
    long id = new Buffer().writeUtf8(traceId).readHexadecimalUnsignedLong();
    List<List<Span>> traces = this.spanStore.getTracesByIds(Collections.singletonList(id));

    if (traces.isEmpty()) {
      throw new TraceNotFoundException(traceId, id);
    }
    return Codec.JSON.writeSpans(traces.get(0));
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

  @ExceptionHandler(MalformedSpansException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public void malformedSpans() {
  }

  static class MalformedSpansException extends RuntimeException {
    public MalformedSpansException(String mediaType) {
      super("List of spans was malformed for media type " + mediaType);
    }
  }
}
