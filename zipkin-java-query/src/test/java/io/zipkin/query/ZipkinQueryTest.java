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
package io.zipkin.query;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import io.zipkin.Trace;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

import static io.zipkin.Constants.CLIENT_RECV;
import static io.zipkin.Constants.CLIENT_SEND;
import static io.zipkin.Constants.SERVER_RECV;
import static io.zipkin.Constants.SERVER_SEND;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ZipkinQueryTest {

  private final Charset UTF_8 = Charset.forName("UTF-8");

  protected abstract ZipkinQuery query();

  protected abstract void reload(Iterable<Span> spans);

  @Before
  public void load() {
    List<Span> spans = new ArrayList<>();
    spans.addAll(trace1.spans());
    spans.addAll(trace5.spans());
    reload(spans);
  }

  Endpoint web = Endpoint.builder()
      .ipv4(0x7f000001 /* 127.0.0.1 */)
      .port((short) 8080)
      .serviceName("web").build();
  Endpoint app = Endpoint.builder(web)
      .port((short) 9411)
      .serviceName("app").build();
  Endpoint db = Endpoint.builder(web)
      .port((short) 3306)
      .serviceName("db").build();

  Annotation webSR = Annotation.builder().timestamp(10).value(SERVER_RECV).host(web).build();
  BinaryAnnotation httpUri = BinaryAnnotation.builder()
      .key("http.uri").value("/foo".getBytes())
      .type(BinaryAnnotation.Type.STRING).host(web).build();
  Annotation webSS = Annotation.builder(webSR).timestamp(21).value(SERVER_SEND).build();
  BinaryAnnotation httpResponse = BinaryAnnotation.builder()
      .key("http.responseCode").value(new byte[] {0, (byte) 200})
      .type(BinaryAnnotation.Type.I16).host(web).build();
  List<Span> collection1 = asList(
      Span.builder()
          .traceId(1L)
          .name("GET")
          .id(1L)
          .annotations(asList(webSR))
          .binaryAnnotations(asList(httpUri))
          .build(),
      Span.builder()
          .traceId(1L)
          .name("/foo")
          .id(1L)
          .annotations(asList(webSS))
          .binaryAnnotations(asList(httpResponse))
          .build()
  );

  long afterWebSS = webSS.timestamp() + 1;

  Annotation webCS = Annotation.builder().timestamp(12).value(CLIENT_SEND).host(web).build();
  Annotation webCR = Annotation.builder(webCS).timestamp(20).value(CLIENT_RECV).build();
  List<Span> collection2 = asList(
      Span.builder()
          .traceId(1L)
          .name("GET Book")
          .id(2L)
          .parentId(1L)
          .annotations(asList(webCS))
          .binaryAnnotations(asList())
          .build(),
      Span.builder()
          .traceId(1L)
          .name("GET Book")
          .id(2L)
          .parentId(1L)
          .annotations(asList(webCR))
          .binaryAnnotations(asList())
          .build()
  );

  Annotation appSR = Annotation.builder().timestamp(14).value(SERVER_RECV).host(app).build();
  Annotation appSS = Annotation.builder(appSR).timestamp(18).value(SERVER_SEND).build();
  List<Span> collection3 = asList(
      Span.builder()
          .traceId(1L)
          .name("GET Book")
          .id(2L)
          .parentId(1L)
          .annotations(asList(appSR))
          .binaryAnnotations(asList())
          .build(),
      Span.builder()
          .traceId(1L)
          .name("GET Book")
          .id(2L)
          .parentId(1L)
          .annotations(asList(appSS))
          .binaryAnnotations(asList())
          .build()
  );

  Annotation dbSR = Annotation.builder().timestamp(13).value(SERVER_RECV).host(db).build();
  Annotation dbSS = Annotation.builder(dbSR).timestamp(15).value(SERVER_SEND).build();
  List<Span> collection4 = asList(
      Span.builder()
          .traceId(1L)
          .name("QUERY BOOK")
          .id(3L)
          .parentId(2L)
          .annotations(asList(dbSR))
          .binaryAnnotations(asList())
          .build(),
      Span.builder()
          .traceId(1L)
          .name("QUERY BOOK")
          .id(3L)
          .parentId(2L)
          .annotations(asList(dbSS))
          .binaryAnnotations(asList())
          .build()
  );

  Annotation dbSR2 = Annotation.builder(dbSR).timestamp(20).build();
  Annotation dbSS2 = Annotation.builder(dbSS).timestamp(21).build();
  List<Span> collection5 = asList(
      Span.builder()
          .traceId(5L)
          .name("QUERY EGG")
          .id(7L)
          .parentId(6L)
          .annotations(asList(dbSR2))
          .binaryAnnotations(asList())
          .build(),
      Span.builder()
          .traceId(5L)
          .name("QUERY EGG")
          .id(7L)
          .parentId(6L)
          .annotations(asList(dbSS2))
          .binaryAnnotations(asList())
          .build()
  );

  Trace trace1 = Trace.create(Stream.of(collection1, collection2, collection3, collection4)
      .flatMap(List::stream).collect(Collectors.toList()));
  Trace trace5 = Trace.create(collection5);

  @Test(expected = IllegalStateException.class)
  public void getTraces_null_service_name() {
    query().getTraces(QueryRequest.builder()
        .spanName("span")
        .annotations(asList())
        .binaryAnnotations(emptyMap())
        .endTs(afterWebSS)
        .limit(100).build());
  }

  @Test
  public void getTraces_span_name() {
    assertThat(query().getTraces(QueryRequest.builder()
        .serviceName("db")
        .spanName("QUERY EGG")
        .annotations(asList())
        .binaryAnnotations(emptyMap())
        .endTs(afterWebSS)
        .limit(100).build())).containsExactly(trace5);
  }

  @Test
  public void getTraces_service_name() {
    assertThat(query().getTraces(QueryRequest.builder()
        .serviceName("db")
        .spanName("QUERY EGG")
        .annotations(asList())
        .binaryAnnotations(emptyMap())
        .endTs(afterWebSS)
        .limit(100).build())).containsExactly(trace5);
  }

  @Test
  public void getTraces_annotation_name() {
    assertThat(query().getTraces(QueryRequest.builder()
        .serviceName("web")
        .annotations(asList(SERVER_SEND))
        .binaryAnnotations(emptyMap())
        .endTs(afterWebSS)
        .limit(100).build())).containsExactly(trace1);
  }

  @Test
  public void getTraces_annotation_name_and_value() {
    Map<String, String> binaryAnnotations = new LinkedHashMap<>();
    binaryAnnotations.put(httpUri.key(), new String(httpUri.value(), UTF_8));
    // NOTE: Thrift accepts the whole struct, but scala impl only pays attention to key and id
    assertThat(query().getTraces(QueryRequest.builder()
        .serviceName("web")
        .annotations(asList())
        .binaryAnnotations(binaryAnnotations)
        .endTs(afterWebSS)
        .limit(100).build())).containsExactly(trace1);
  }

  @Test
  public void getTracesByIds() {
    assertThat(query().getTracesByIds(asList(1L, 3L), true))
        .containsExactly(trace1);
  }

  @Test
  public void getSpanNames() {
    assertThat(query().getSpanNames("db"))
        .containsExactly("QUERY BOOK", "QUERY EGG");
  }

  @Test
  public void getSpanNames_null() {
    assertThat(query().getSpanNames(null))
        .isEmpty();
  }

  @Test
  public void getSpanNames_notFound() {
    assertThat(query().getSpanNames("booboo"))
        .isEmpty();
  }

  @Test
  public void getServiceNames() {
    assertThat(query().getServiceNames())
        .containsExactly("web", "app", "db");
  }
}
