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
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;

public class V1SpanConverterTest {
  Endpoint kafka = Endpoint.newBuilder().serviceName("kafka").build();
  V1SpanConverter v1SpanConverter = new V1SpanConverter();

  @Test public void convert_ma() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "mr", BACKEND)
      .addBinaryAnnotation("ma",  kafka)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.CONSUMER)
      .timestamp(1472470996199000L)
      .localEndpoint(BACKEND)
      .remoteEndpoint(kafka)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test public void convert_sa() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "cs", FRONTEND)
      .addBinaryAnnotation("sa",  BACKEND)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.CLIENT)
      .timestamp(1472470996199000L)
      .localEndpoint(FRONTEND)
      .remoteEndpoint(BACKEND)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test public void convert_ca() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "sr", BACKEND)
      .addBinaryAnnotation("ca",  FRONTEND)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.SERVER)
      .timestamp(1472470996199000L)
      .localEndpoint(BACKEND)
      .remoteEndpoint(FRONTEND)
      .shared(true)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  // Following 3 tests show leniency for old versions of zipkin-ruby which serialized address binary
  // annotations as "1" instead of true
  @Test public void convert_ma_incorrect_value() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "mr", BACKEND)
      .addBinaryAnnotation("ma", "1", kafka)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.CONSUMER)
      .timestamp(1472470996199000L)
      .localEndpoint(BACKEND)
      .remoteEndpoint(kafka)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test public void convert_sa_incorrect_value() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "cs", FRONTEND)
      .addBinaryAnnotation("sa", "1", BACKEND)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.CLIENT)
      .timestamp(1472470996199000L)
      .localEndpoint(FRONTEND)
      .remoteEndpoint(BACKEND)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }

  @Test public void convert_ca_incorrect_value() {
    V1Span v1 = V1Span.newBuilder()
      .traceId(1L)
      .id(2L)
      .addAnnotation(1472470996199000L, "sr", BACKEND)
      .addBinaryAnnotation("ca", "1", FRONTEND)
      .build();

    Span v2 = Span.newBuilder().traceId("1").id("2")
      .kind(Kind.SERVER)
      .timestamp(1472470996199000L)
      .localEndpoint(BACKEND)
      .remoteEndpoint(FRONTEND)
      .shared(true)
      .build();

    assertThat(v1SpanConverter.convert(v1)).containsExactly(v2);
  }
}
