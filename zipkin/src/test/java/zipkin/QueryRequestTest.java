/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryRequestTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void serviceNameCantBeNull() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("serviceName was empty");

    new QueryRequest.Builder((String) null).build();
  }

  @Test
  public void serviceNameCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("serviceName was empty");

    new QueryRequest.Builder("").build();
  }

  @Test
  public void spanNameCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("spanName was empty");

    new QueryRequest.Builder("foo").spanName("").build();
  }

  @Test
  public void annotationCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("annotation was empty");

    new QueryRequest.Builder("foo").addAnnotation("").build();
  }

  @Test
  public void binaryAnnotationKeyCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("binary annotation key was empty");

    new QueryRequest.Builder("foo").addBinaryAnnotation("", "bar").build();
  }

  @Test
  public void binaryAnnotationValueCantBeEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("binary annotation value was empty");

    new QueryRequest.Builder("foo").addBinaryAnnotation("foo", "").build();
  }

  @Test
  public void endTsMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("endTs should be positive, in epoch microseconds: was 0");

    new QueryRequest.Builder("foo").endTs(0L).build();
  }

  @Test
  public void limitMustBePositive() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("limit should be positive: was 0");

    new QueryRequest.Builder("foo").limit(0).build();
  }

  @Test
  public void annotationQueryRoundTrip() {
    String annotationQuery = "http.method=GET and finagle.retry";

    QueryRequest request =
        new QueryRequest.Builder("security-service").parseAnnotationQuery(annotationQuery).build();

    assertThat(request.binaryAnnotations)
        .containsEntry("http.method", "GET")
        .hasSize(1);
    assertThat(request.annotations)
        .containsExactly("finagle.retry");

    assertThat(request.toAnnotationQuery())
        .isEqualTo(annotationQuery);
  }

  @Test
  public void toAnnotationQueryWhenNoInputIsNull() {
    QueryRequest request = new QueryRequest.Builder("security-service").build();

    assertThat(request.toAnnotationQuery())
        .isNull();
  }
}
