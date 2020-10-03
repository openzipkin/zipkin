/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.v1;

import org.junit.Test;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class V1SpanTest {
  V1Span.Builder builder = V1Span.newBuilder().traceId("1").id("1");

  @Test public void annotationEndpoint_emptyToNull() {
    assertThat(builder.addAnnotation(1, "foo", Endpoint.newBuilder().build()).annotations)
      .extracting(V1Annotation::endpoint)
      .containsOnlyNulls();
  }

  @Test public void binaryAnnotationEndpoint_emptyToNull() {
    assertThat(
      builder.addBinaryAnnotation("foo", "bar", Endpoint.newBuilder().build()).binaryAnnotations)
      .extracting(V1BinaryAnnotation::endpoint)
      .containsOnlyNulls();
  }

  @Test public void binaryAnnotationEndpoint_ignoresEmptyAddress() {
    assertThat(
      builder.addBinaryAnnotation("ca", Endpoint.newBuilder().build()).binaryAnnotations)
      .isNull();
  }
}
