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
package zipkin2.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class QueryRequestTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  QueryRequest.Builder queryBuilder = QueryRequest.newBuilder().endTs(TestObjects.TODAY).lookback(60).limit(10);
  Span span = Span.newBuilder().traceId("10").id("10").name("receive")
    .localEndpoint(Endpoint.newBuilder().serviceName("app").build())
    .kind(Span.Kind.CONSUMER)
    .timestamp(TestObjects.TODAY * 1000)
    .build();

  @Test public void serviceNameCanBeNull() {
    assertThat(queryBuilder.build().serviceName())
      .isNull();
  }

  @Test public void serviceName_coercesEmptyToNull() {
    assertThat(queryBuilder.serviceName("").build().serviceName())
      .isNull();
  }

  @Test public void remoteServiceNameCanBeNull() {
    assertThat(queryBuilder.build().remoteServiceName())
      .isNull();
  }

  @Test public void remoteServiceName_coercesEmptyToNull() {
    assertThat(queryBuilder.remoteServiceName("").build().remoteServiceName())
      .isNull();
  }

  @Test public void spanName_coercesAllToNull() {
    assertThat(queryBuilder.spanName("all").build().spanName())
      .isNull();
  }

  @Test public void spanName_coercesEmptyToNull() {
    assertThat(queryBuilder.spanName("").build().spanName())
      .isNull();
  }

  @Test public void annotationQuerySkipsEmptyKeys() {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("", "bar");

    assertThat(queryBuilder.annotationQuery(query).build().annotationQuery())
      .isEmpty();
  }

  @Test public void endTsMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("endTs <= 0");

    queryBuilder.endTs(0L).build();
  }

  @Test public void lookbackMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("lookback <= 0");

    queryBuilder.lookback(0).build();
  }

  @Test public void limitMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("limit <= 0");

    queryBuilder.limit(0).build();
  }

  @Test public void annotationQuery_roundTrip() {
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

  @Test public void annotationQuery_missingValue() {
    String annotationQuery = "http.method=";

    QueryRequest request = queryBuilder
      .serviceName("security-service")
      .parseAnnotationQuery(annotationQuery)
      .build();

    assertThat(request.annotationQuery())
      .containsKey("http.method");
  }

  @Test public void annotationQueryWhenNoInputIsEmpty() {
    assertThat(queryBuilder.build().annotationQuery())
      .isEmpty();
  }

  @Test public void minDuration_mustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("minDuration <= 0");

    queryBuilder.minDuration(0L).build();
  }

  @Test public void maxDuration_onlyWithMinDuration() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("maxDuration is only valid with minDuration");

    queryBuilder.maxDuration(0L).build();
  }

  @Test public void maxDuration_greaterThanOrEqualToMinDuration() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("maxDuration < minDuration");

    queryBuilder.minDuration(1L).maxDuration(0L).build();
  }

  @Test public void test_matchesTimestamp() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(asList(span)))
      .isTrue();
  }

  @Test public void test_rootSpanNotFirst() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(asList(
      span.toBuilder().id("2").parentId(span.id()).timestamp(null).build(),
      span
    ))).isTrue();
  }

  @Test public void test_noRootSpanLeastWins() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(asList(
      span.toBuilder().id("2").parentId(span.id()).timestamp(span.timestamp() + TestObjects.DAY * 1000).build(),
      span.toBuilder().id("3").parentId(span.id()).build()
    ))).isTrue();
  }

  @Test public void test_noTimestamp() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(asList(span.toBuilder().timestamp(null).build())))
      .isFalse();
  }

  @Test public void test_timestampPastLookback() {
    QueryRequest request = queryBuilder
      .endTs(TestObjects.TODAY + 70)
      .build();

    assertThat(request.test(asList(span)))
      .isFalse();
  }

  @Test public void test_wrongServiceName() {
    QueryRequest request = queryBuilder
      .serviceName("aloha")
      .build();

    assertThat(request.test(asList(span)))
      .isFalse();
  }

  @Test public void test_spanName() {
    QueryRequest request = queryBuilder
      .spanName("aloha")
      .build();

    assertThat(request.test(asList(span)))
      .isFalse();

    assertThat(request.test(asList(span.toBuilder().name("aloha").build())))
      .isTrue();
  }

  @Test public void test_remoteServiceName() {
    QueryRequest request = queryBuilder
      .remoteServiceName("db")
      .build();

    assertThat(request.test(asList(span)))
      .isFalse();

    assertThat(request.test(asList(span.toBuilder().remoteEndpoint(Endpoint.newBuilder().serviceName("db").build()).build())))
      .isTrue();
  }

  @Test public void test_minDuration() {
    QueryRequest request = queryBuilder
      .minDuration(100L)
      .build();

    assertThat(request.test(asList(span.toBuilder().duration(99L).build())))
      .isFalse();

    assertThat(request.test(asList(span.toBuilder().duration(100L).build())))
      .isTrue();
  }

  @Test public void test_maxDuration() {
    QueryRequest request = queryBuilder
      .minDuration(100L)
      .maxDuration(110L)
      .build();

    assertThat(request.test(asList(span.toBuilder().duration(99L).build())))
      .isFalse();

    assertThat(request.test(asList(span.toBuilder().duration(100L).build())))
      .isTrue();

    assertThat(request.test(asList(span.toBuilder().duration(111L).build())))
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

  @Test public void test_annotationQuery_tagKey() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("baz").build();

    assertThat(query.test(asList(foo)))
      .isFalse();
    assertThat(query.test(asList(barAndFoo)))
      .isFalse();
    assertThat(query.test(asList(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(asList(fooAndBazAndQux)))
      .isTrue();
  }

  @Test public void test_annotationQuery_annotation() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo").build();

    assertThat(query.test(asList(foo)))
      .isTrue();
    assertThat(query.test(asList(barAndFoo)))
      .isTrue();
    assertThat(query.test(asList(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(asList(fooAndBazAndQux)))
      .isTrue();
  }

  @Test public void test_annotationQuery_twoAnnotation() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo and bar").build();

    assertThat(query.test(asList(foo)))
      .isFalse();
    assertThat(query.test(asList(barAndFoo)))
      .isTrue();
    assertThat(query.test(asList(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(asList(fooAndBazAndQux)))
      .isFalse();
  }

  @Test public void test_annotationQuery_annotationsAndTag() {
    QueryRequest query = queryBuilder
      .parseAnnotationQuery("foo and bar and baz=qux").build();

    assertThat(query.test(asList(foo)))
      .isFalse();
    assertThat(query.test(asList(barAndFoo)))
      .isFalse();
    assertThat(query.test(asList(barAndFooAndBazAndQux)))
      .isTrue();
    assertThat(query.test(asList(fooAndBazAndQux)))
      .isFalse();
  }
}
