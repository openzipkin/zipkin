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
package io.zipkin;

import io.zipkin.internal.Util.Serializer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import okio.Buffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static io.zipkin.internal.Util.writeJsonList;

/**
 * Implements the json api used by {@code zipkin-web}.
 *
 * See com.twitter.zipkin.query.ZipkinQueryController
 */
@Controller
@RequestMapping("/api/v1")
public class ZipkinQueryApiV1 {

  static final Serializer<List<Span>> TRACE_TO_JSON = writeJsonList(Codec.JSON::writeSpan);
  static final Serializer<List<List<Span>>> TRACES_TO_JSON = writeJsonList(TRACE_TO_JSON);

  private final SpanStore spanStore;

  @Inject
  ZipkinQueryApiV1(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @RequestMapping(value = "/dependencies", method = RequestMethod.GET)
  @ResponseBody
  public List<String> getDependencies(@RequestParam(value = "startTs", required = false, defaultValue = "0") long startTs,
                                      @RequestParam(value = "endTs", required = true) long endTs) {
    return Arrays.asList();
  }

  @RequestMapping(value = "/services", method = RequestMethod.GET)
  @ResponseBody
  public List<String> getServiceNames() {
    return spanStore.getServiceNames();
  }

  @RequestMapping(value = "/spans", method = RequestMethod.GET)
  @ResponseBody
  public List<String> getSpanNames(@RequestParam(value = "serviceName", required = true) String serviceName) {
    return spanStore.getSpanNames(serviceName);
  }

  @RequestMapping(value = "/traces", method = RequestMethod.GET)
  @ResponseBody
  public ResponseEntity<byte[]> getTraces(@RequestParam(value = "serviceName", required = true) String serviceName,
                                          @RequestParam(value = "spanName", defaultValue = "all") String spanName,
                                          @RequestParam(value = "annotationQuery") String annotationQuery,
                                          @RequestParam(value = "endTs") Long endTs,
                                          @RequestParam(value = "limit") Integer limit) {
    QueryRequest.Builder builder = new QueryRequest.Builder()
        .serviceName(serviceName)
        .spanName(spanName.equals("all") ? null : spanName)
        .endTs(endTs)
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
    return ResponseEntity.ok(TRACES_TO_JSON.apply(spanStore.getTraces(builder.build())));
  }

  @RequestMapping(value = "/trace/{traceId}", method = RequestMethod.GET)
  @ResponseBody
  public ResponseEntity<byte[]> getTrace(@PathVariable String traceId) {
    long id = new Buffer().writeUtf8(traceId).readHexadecimalUnsignedLong();
    List<List<Span>> traces = spanStore.getTracesByIds(Collections.singletonList(id));

    if (traces.isEmpty()) {
      return new ResponseEntity(HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.ok(TRACE_TO_JSON.apply(traces.get(0)));
  }
}
