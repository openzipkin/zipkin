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
package zipkin.internal.v2.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Constants;
import zipkin.internal.v2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.TODAY;
import static zipkin.TraceKeys.HTTP_METHOD;

public class QueryRequestTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  QueryRequest.Builder queryBuilder = QueryRequest.newBuilder().endTs(TODAY).lookback(60).limit(10);
  Span span = Span.builder().traceId(10L).id(10L).name("receive")
    .localEndpoint(APP_ENDPOINT)
    .kind(Span.Kind.CONSUMER)
    .timestamp(TODAY * 1000)
    .build();

  @Test public void serviceNameCanBeNull() {
    assertThat(queryBuilder.build().serviceName())
      .isNull();
  }

  @Test public void serviceName_coercesEmptyToNull() {
    assertThat(queryBuilder.serviceName("").build().serviceName())
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
      .containsEntry(Constants.ERROR, "")
      .containsEntry(HTTP_METHOD, "GET");

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
      .containsKey(HTTP_METHOD);
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

  @Test public void test_noTimestamp() {
    QueryRequest request = queryBuilder
      .build();

    assertThat(request.test(asList(span.toBuilder().timestamp(null).build())))
      .isFalse();
  }

  @Test public void test_timestampPastLookback() {
    QueryRequest request = queryBuilder
      .endTs(TODAY + 70)
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
}
