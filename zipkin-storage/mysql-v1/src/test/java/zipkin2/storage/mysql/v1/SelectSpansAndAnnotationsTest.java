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
package zipkin2.storage.mysql.v1;

import org.jooq.Record4;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

public class SelectSpansAndAnnotationsTest {
  @Test
  public void processAnnotationRecord_nulls() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(null, null, null, null);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder)
      .usingRecursiveComparison().isEqualTo(V1Span.newBuilder().traceId(1).id(1));
  }

  @Test
  public void processAnnotationRecord_annotation() {
    Record4<Integer, Long, String, byte[]> annotationRecord = annotationRecord(-1, 0L, "foo", null);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().annotations().get(0))
        .isEqualTo(V1Annotation.create(0L, "foo", null));
  }

  @Test
  public void processAnnotationRecord_tag() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(6, null, "foo", new byte[0]);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().binaryAnnotations().get(0))
        .isEqualTo(V1BinaryAnnotation.createString("foo", "", null));
  }

  @Test
  public void processAnnotationRecord_address() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(0, null, "ca", new byte[] {1});
    Endpoint ep = Endpoint.newBuilder().serviceName("foo").build();

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, ep);

    assertThat(builder.build().binaryAnnotations().get(0))
        .isEqualTo(V1BinaryAnnotation.createAddress("ca", ep));
  }

  @Test
  public void processAnnotationRecord_address_skipMissingEndpoint() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(0, null, "ca", new byte[] {1});

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().binaryAnnotations()).isEmpty();
  }

  @Test
  public void processAnnotationRecord_address_skipWrongKey() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(0, null, "sr", new byte[] {1});
    Endpoint ep = Endpoint.newBuilder().serviceName("foo").build();

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, ep);

    assertThat(builder.build().binaryAnnotations()).isEmpty();
  }

  static Record4<Integer, Long, String, byte[]> annotationRecord(
      Integer type, Long timestamp, String key, byte[] value) {
    return DSL.using(SQLDialect.MYSQL)
        .newRecord(
            ZIPKIN_ANNOTATIONS.A_TYPE,
            ZIPKIN_ANNOTATIONS.A_TIMESTAMP,
            ZIPKIN_ANNOTATIONS.A_KEY,
            ZIPKIN_ANNOTATIONS.A_VALUE)
        .value1(type)
        .value2(timestamp)
        .value3(key)
        .value4(value);
  }

  @Test
  public void endpoint_justIpv4() {
    Record4<String, Integer, Short, byte[]> endpointRecord =
        endpointRecord("", 127 << 24 | 1, (short) 0, new byte[0]);

    assertThat(SelectSpansAndAnnotations.endpoint(endpointRecord))
        .isEqualTo(Endpoint.newBuilder().ip("127.0.0.1").build());
  }

  static Record4<String, Integer, Short, byte[]> endpointRecord(
      String serviceName, Integer ipv4, Short port, byte[] ipv6) {
    return DSL.using(SQLDialect.MYSQL)
        .newRecord(
            ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME,
            ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4,
            ZIPKIN_ANNOTATIONS.ENDPOINT_PORT,
            ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6)
        .value1(serviceName)
        .value2(ipv4)
        .value3(port)
        .value4(ipv6);
  }
}
