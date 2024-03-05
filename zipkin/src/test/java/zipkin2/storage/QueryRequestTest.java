/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryRequestTest {
  QueryRequest.Builder queryBuilder =
    QueryRequest.newBuilder().endTs(TestObjects.TODAY).lookback(60).limit(10);
  Span span = Span.newBuilder().traceId("10").id("10").name("receive")
    .localEndpoint(Endpoint.newBuilder().serviceName("app").build())
    .kind(Span.Kind.CONSUMER)
    .timestamp(TestObjects.TODAY * 1000)
    .build();

  @Test void serviceNameCanBeNull() {
    assertThat(queryBuilder.build().serviceName())
      .isNull();
  }

  @Test void serviceName_coercesEmptyToNull() {
    assertThat(queryBuilder.serviceName("").build().serviceName())
      .isNull();
  }

  @Test void remoteServiceNameCanBeNull() {
    assertThat(queryBuilder.build().remoteServiceName())
      .isNull();
  }

  @Test void remoteServiceName_coercesEmptyToNull() {
    assertThat(queryBuilder.remoteServiceName("").build().remoteServiceName())
      .isNull();
  }

  @Test void spanName_coercesAllToNull() {
    assertThat(queryBuilder.spanName("all").build().spanName())
      .isNull();
  }

  @Test void spanName_coercesEmptyToNull() {
    assertThat(queryBuilder.spanName("").build().spanName())
      .isNull();
  }

  @Test void annotationQuerySkipsEmptyKeys() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("", "bar");

    assertThat(queryBuilder.annotationQuery(query).build().annotationQueryString())
      .isNull();
  }

  @Test void annotationQueryTrimsSpaces() {
    // spaces in http.path mixed with 'and'
    assertThat(queryBuilder.parseAnnotationQuery("fo and o and bar and http.path = /a ").annotationQuery)
      .containsOnly(entry("fo", ""), entry("o", ""), entry("bar", ""), entry("http.path", "/a"));
    // http.path in the beginning, more spaces
    assertThat(queryBuilder.parseAnnotationQuery(" http.path = /a   and fo and o   and bar").annotationQuery)
      .containsOnly(entry("fo", ""), entry("o", ""), entry("bar", ""), entry("http.path", "/a"));
    // @adriancole said this would be hard to parse, annotation containing spaces
    assertThat(queryBuilder.parseAnnotationQuery("L O L").annotationQuery)
      .containsOnly(entry("L O L", ""));
    // annotation with spaces combined with tag
    assertThat(queryBuilder.parseAnnotationQuery("L O L and http.path = /a").annotationQuery)
      .containsOnly(entry("L O L", ""), entry("http.path", "/a"));
    assertThat(queryBuilder.parseAnnotationQuery("bar =123 and L O L and http.path = /a and A B C").annotationQuery)
      .containsOnly(entry("L O L", ""), entry("http.path", "/a"), entry("bar", "123"), entry("A B C", ""));
  }

  @Test void annotationQueryParameterSpecificity() {
    // when a parameter is specified both as a tag and annotation, the tag wins because it's considered to be more
    // specific
    assertThat(queryBuilder.parseAnnotationQuery("a=123 and a").annotationQuery).containsOnly(entry("a", "123"));
    assertThat(queryBuilder.parseAnnotationQuery("a and a=123").annotationQuery).containsOnly(entry("a", "123"));
    // also last tag wins
    assertThat(queryBuilder.parseAnnotationQuery("a=123 and a=456").annotationQuery).containsOnly(entry("a", "456"));
    assertThat(queryBuilder.parseAnnotationQuery("a and a=123 and a=456").annotationQuery).containsOnly(entry("a", "456"));
  }

  @Test void endTsMustBePositive() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.endTs(0L).build();
    });
    assertThat(exception.getMessage()).contains("endTs <= 0");
  }

  @Test void lookbackMustBePositive() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.lookback(0).build();
    });
    assertThat(exception.getMessage()).contains("lookback <= 0");
  }

  @Test void limitMustBePositive() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.limit(0).build();
    });
    assertThat(exception.getMessage()).contains("limit <= 0");
  }

  @Test void annotationQuery_roundTrip() {
    String annotationQuery = "http.method=GET and error";

    QueryRequest request = queryBuilder
      .serviceName("security-service")
      .parseAnnotationQuery(annotationQuery)
      .build();

    assertThat(request.annotationQuery())
      .containsEntry("error", "")
      .containsEntry("http.method", "GET");

    assertThat(request.annotationQueryString())
      .isEqualTo(annotationQuery);
  }

  @Test void annotationQuery_missingValue() {
    String annotationQuery = "http.method=";

    QueryRequest request = queryBuilder
      .serviceName("security-service")
      .parseAnnotationQuery(annotationQuery)
      .build();

    assertThat(request.annotationQuery())
      .containsKey("http.method");
  }

  @Test void annotationQueryWhenNoInputIsEmpty() {
    assertThat(queryBuilder.build().annotationQuery())
      .isEmpty();
  }

  @Test void minDuration_mustBePositive() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.minDuration(0L).build();
    });
    assertThat(exception.getMessage()).contains("minDuration <= 0");
  }

  @Test void maxDuration_onlyWithMinDuration() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.maxDuration(0L).build();
    });
    assertThat(exception.getMessage()).contains("maxDuration is only valid with minDuration");
  }

  @Test void maxDuration_greaterThanOrEqualToMinDuration() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      queryBuilder.minDuration(1L).maxDuration(0L).build();
    });
    assertThat(exception.getMessage()).contains("maxDuration < minDuration");
  }

  @Test void test_matchesTimestamp() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(List.of(span)))
      .isTrue();
  }

  @Test void test_rootSpanNotFirst() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(List.of(
      span.toBuilder().id("2").parentId(span.id()).timestamp(null).build(),
      span
    ))).isTrue();
  }

  @Test void test_noRootSpanLeastWins() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(List.of(
      span.toBuilder().id("2").parentId(span.id()).timestamp(span.timestamp() + TestObjects.DAY * 1000).build(),
      span.toBuilder().id("3").parentId(span.id()).build()
    ))).isTrue();
  }

  @Test void test_noTimestamp() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(List.of(span.toBuilder().timestamp(null).build())))
      .isFalse();
  }

  @Test void test_timestampPastLookback() {
    QueryRequest request = queryBuilder
      .endTs(TestObjects.TODAY + 70)
      .build();

    assertThat(request.test(List.of(span)))
      .isFalse();
  }

  @Test void test_wrongServiceName() {
    QueryRequest request = queryBuilder
      .serviceName("aloha")
      .build();

    assertThat(request.test(List.of(span)))
      .isFalse();
  }

  @Test void test_spanName() {
    QueryRequest request = queryBuilder
      .spanName("aloha")
      .build();

    assertThat(request.test(List.of(span)))
      .isFalse();

    assertThat(request.test(List.of(span.toBuilder().name("aloha").build())))
      .isTrue();
  }

  @Test void test_remoteServiceName() {
    QueryRequest request = queryBuilder
      .remoteServiceName("db")
      .build();

    assertThat(request.test(List.of(span)))
      .isFalse();

    assertThat(request.test(List.of(span.toBuilder().remoteEndpoint(Endpoint.newBuilder().serviceName("db").build()).build())))
      .isTrue();
  }

  @Test void test_minDuration() {
    QueryRequest request = queryBuilder
      .minDuration(100L)
      .build();

    assertThat(request.test(List.of(span.toBuilder().duration(99L).build())))
      .isFalse();

    assertThat(request.test(List.of(span.toBuilder().duration(100L).build())))
      .isTrue();
  }

  @Test void test_maxDuration() {
    QueryRequest request = queryBuilder
      .minDuration(100L)
      .maxDuration(110L)
      .build();

    assertThat(request.test(List.of(span.toBuilder().duration(99L).build())))
      .isFalse();

    assertThat(request.test(List.of(span.toBuilder().duration(100L).build())))
      .isTrue();

    assertThat(request.test(List.of(span.toBuilder().duration(111L).build())))
      .isFalse();
  }

  Span foo = span.toBuilder().traceId("1").name("call1").id("1")
    .addAnnotation(span.timestamp(), "foo").build();
  // would be foo bar, except lexicographically bar precedes foo
  Span barAndFoo = span.toBuilder().traceId("2").name("call2").id("2")
    .addAnnotation(span.timestamp(), "bar")
    .addAnnotation(span.timestamp(), "foo").build();
  Span fooAndBazAndQux = span.toBuilder().traceId("3").name("call3").id("3")
    .addAnnotation(span.timestamp(), "foo")
    .putTag("baz", "qux")
    .build();
  Span barAndFooAndBazAndQux = span.toBuilder().traceId("4").name("call4").id("4")
    .addAnnotation(span.timestamp(), "bar")
    .addAnnotation(span.timestamp(), "foo")
    .putTag("baz", "qux")
    .build();

  @Test void test_annotationQuery_tagKey() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("baz").build();

    assertThat(query.test(List.of(foo)))
      .isFalse();
    assertThat(query.test(List.of(barAndFoo)))
      .isFalse();
    assertThat(query.test(List.of(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(List.of(fooAndBazAndQux)))
      .isTrue();
  }

  @Test void test_annotationQuery_annotation() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo").build();

    assertThat(query.test(List.of(foo)))
      .isTrue();
    assertThat(query.test(List.of(barAndFoo)))
      .isTrue();
    assertThat(query.test(List.of(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(List.of(fooAndBazAndQux)))
      .isTrue();
  }

  @Test void test_annotationQuery_twoAnnotation() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo and bar").build();

    assertThat(query.test(List.of(foo)))
      .isFalse();
    assertThat(query.test(List.of(barAndFoo)))
      .isTrue();
    assertThat(query.test(List.of(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(List.of(fooAndBazAndQux)))
      .isFalse();
  }

  @Test void test_annotationQuery_annotationsAndTag() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo and bar and baz=qux").build();

    assertThat(query.test(List.of(foo)))
      .isFalse();
    assertThat(query.test(List.of(barAndFoo)))
      .isFalse();
    assertThat(query.test(List.of(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(List.of(fooAndBazAndQux)))
      .isFalse();
  }
}
