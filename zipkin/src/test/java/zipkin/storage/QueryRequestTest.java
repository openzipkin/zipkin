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
package zipkin.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.TODAY;
import static zipkin.TraceKeys.HTTP_METHOD;

public class QueryRequestTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void serviceNameCanBeNull() {
    assertThat(QueryRequest.builder().build().serviceName)
        .isNull();
  }

  @Test
  public void serviceNameCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("serviceName was empty");

    QueryRequest.builder().serviceName("").build();
  }

  @Test
  public void spanNameCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("spanName was empty");

    QueryRequest.builder().serviceName("foo").spanName("").build();
  }

  @Test
  public void annotationCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("annotation was empty");

    QueryRequest.builder().serviceName("foo").addAnnotation("").build();
  }

  /**
   * Particularly in the case of cassandra, indexing boundary annotations isn't fruitful work, and
   * not helpful to users. Nevertheless we should ensure an unlikely caller gets an exception.
   */
  @Test
  public void annotationCantBeCore() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("queries cannot be refined by core annotations: sr");

    QueryRequest.builder().serviceName("foo").addAnnotation(Constants.SERVER_RECV).build();
  }

  @Test
  public void binaryAnnotationKeyCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("binary annotation key was empty");

    QueryRequest.builder().serviceName("foo").addBinaryAnnotation("", "bar").build();
  }

  @Test
  public void binaryAnnotationValueCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("binary annotation value for foo was empty");

    QueryRequest.builder().serviceName("foo").addBinaryAnnotation("foo", "").build();
  }

  @Test
  public void endTsMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("endTs should be positive, in epoch microseconds: was 0");

    QueryRequest.builder().serviceName("foo").endTs(0L).build();
  }

  @Test
  public void limitMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("limit should be positive: was 0");

    QueryRequest.builder().serviceName("foo").limit(0).build();
  }

  @Test
  public void annotationQuery_roundTrip() {
    String annotationQuery = "http.method=GET and error";

    QueryRequest request =
        QueryRequest.builder().serviceName("security-service").parseAnnotationQuery(annotationQuery).build();
    
    assertThat(request.binaryAnnotations)
        .containsEntry(HTTP_METHOD, "GET")
        .hasSize(1);
    assertThat(request.annotations)
        .containsExactly(Constants.ERROR);

    assertThat(request.toAnnotationQuery())
        .isEqualTo(annotationQuery);
  }

  @Test
  public void annotationQuery_complexValue() {
    String annotationQuery = "http.method=GET=1 and error";

    QueryRequest request = QueryRequest.builder().serviceName("security-service")
        .parseAnnotationQuery(annotationQuery).build();
    
    assertThat(request.binaryAnnotations)
        .containsEntry(HTTP_METHOD, "GET=1")
        .hasSize(1);
    assertThat(request.annotations)
        .containsExactly(Constants.ERROR);

    assertThat(request.toAnnotationQuery())
        .isEqualTo(annotationQuery);
  }

  @Test
  public void annotationQuery_missingValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("binary annotation value for http.method was empty");

    String annotationQuery = "http.method=";

    QueryRequest request =
        QueryRequest.builder().serviceName("security-service").parseAnnotationQuery(annotationQuery).build();

    assertThat(request.annotations)
        .containsExactly(HTTP_METHOD);
  }

  @Test
  public void toAnnotationQueryWhenNoInputIsNull() {
    QueryRequest request = QueryRequest.builder().serviceName("security-service").build();

    assertThat(request.toAnnotationQuery())
        .isNull();
  }

  @Test
  public void minDuration_mustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("minDuration must be a positive number of microseconds");

    QueryRequest.builder().serviceName("foo").minDuration(0L).build();
  }

  @Test
  public void maxDuration_onlyWithMinDuration() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("maxDuration is only valid with minDuration");

    QueryRequest.builder().serviceName("foo").maxDuration(0L).build();
  }

  @Test
  public void maxDuration_greaterThanOrEqualToMinDuration() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("maxDuration should be >= minDuration");

    QueryRequest.builder().serviceName("foo").minDuration(1L).maxDuration(0L).build();
  }

  /** When a span comes in without a timestamp, use the implicit one based on annotations. */
  @Test
  public void matchesImplicitTimestamp() {
    Span asyncReceive = Span.builder().traceId(10L).id(10L).name("receive")
        .addAnnotation(Annotation.create((TODAY) * 1000, SERVER_RECV, APP_ENDPOINT))
        .build();

    QueryRequest request = QueryRequest.builder().endTs(TODAY).build();

    assertThat(request.test(asList(asyncReceive)))
        .isTrue();
  }
}
