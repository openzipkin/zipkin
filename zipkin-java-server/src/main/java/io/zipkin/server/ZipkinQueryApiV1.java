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
import javax.inject.Inject;
import okio.Buffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
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

  static final Serializer<List<Span>> TRACE_TO_JSON = writeJsonList(Codec.JSON::writeSpan);
  static final Serializer<List<List<Span>>> TRACES_TO_JSON = writeJsonList(TRACE_TO_JSON);
  static final Serializer<List<DependencyLink>> DEPENDENCY_LINKS_TO_JSON = writeJsonList(Codec.JSON::writeDependencyLink);

  private final SpanStore spanStore;

  @Inject
  ZipkinQueryApiV1(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getDependencies(
      @RequestParam(value = "startTs", required = false, defaultValue = "0") long startTs,
      @RequestParam(value = "endTs", required = true) long endTs) {
    return DEPENDENCY_LINKS_TO_JSON.apply(spanStore.getDependencies(startTs != 0 ? startTs : null, endTs).links);
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

  @RequestMapping(value = "/traces", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
  public byte[] getTraces(
      @RequestParam(value = "serviceName", required = true) String serviceName,
      @RequestParam(value = "spanName", defaultValue = "all") String spanName,
      @RequestParam(value = "annotationQuery", required = false) String annotationQuery,
      @RequestParam(value = "endTs", required = false) Long endTs,
      @RequestParam(value = "limit", required = false) Integer limit) {
    QueryRequest.Builder builder = new QueryRequest.Builder().serviceName(serviceName)
        .spanName(spanName.equals("all") ? null : spanName).endTs(endTs).limit(limit);
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
    long id = new Buffer().writeUtf8(traceId).readHexadecimalUnsignedLong();
    List<List<Span>> traces = this.spanStore.getTracesByIds(Collections.singletonList(id));

    if (traces.isEmpty()) {
      throw new TraceNotFoundException(traceId, id);
    }
    return TRACE_TO_JSON.apply(traces.get(0));
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
