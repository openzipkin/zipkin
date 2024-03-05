/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.v1;

import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

class V1SpanTest {
  V1Span.Builder builder = V1Span.newBuilder().traceId("1").id("1");

  @Test void annotationEndpoint_emptyToNull() {
    assertThat(builder.addAnnotation(1, "foo", Endpoint.newBuilder().build()).annotations)
      .extracting(V1Annotation::endpoint)
      .containsOnlyNulls();
  }

  @Test void binaryAnnotationEndpoint_emptyToNull() {
    assertThat(
      builder.addBinaryAnnotation("foo", "bar", Endpoint.newBuilder().build()).binaryAnnotations)
      .extracting(V1BinaryAnnotation::endpoint)
      .containsOnlyNulls();
  }

  @Test void binaryAnnotationEndpoint_ignoresEmptyAddress() {
    assertThat(
      builder.addBinaryAnnotation("ca", Endpoint.newBuilder().build()).binaryAnnotations)
      .isNull();
  }
}
