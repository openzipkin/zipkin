/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import org.jooq.Record4;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

class SelectSpansAndAnnotationsTest {
  @Test void processAnnotationRecord_nulls() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(null, null, null, null);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder)
      .usingRecursiveComparison().isEqualTo(V1Span.newBuilder().traceId(1).id(1));
  }

  @Test void processAnnotationRecord_annotation() {
    Record4<Integer, Long, String, byte[]> annotationRecord = annotationRecord(-1, 0L, "foo", null);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().annotations().get(0))
        .isEqualTo(V1Annotation.create(0L, "foo", null));
  }

  @Test void processAnnotationRecord_tag() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(6, null, "foo", new byte[0]);

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().binaryAnnotations().get(0))
        .isEqualTo(V1BinaryAnnotation.createString("foo", "", null));
  }

  @Test void processAnnotationRecord_address() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(0, null, "ca", new byte[] {1});
    Endpoint ep = Endpoint.newBuilder().serviceName("foo").build();

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, ep);

    assertThat(builder.build().binaryAnnotations().get(0))
        .isEqualTo(V1BinaryAnnotation.createAddress("ca", ep));
  }

  @Test void processAnnotationRecord_address_skipMissingEndpoint() {
    Record4<Integer, Long, String, byte[]> annotationRecord =
        annotationRecord(0, null, "ca", new byte[] {1});

    V1Span.Builder builder = V1Span.newBuilder().traceId(1).id(1);
    SelectSpansAndAnnotations.processAnnotationRecord(annotationRecord, builder, null);

    assertThat(builder.build().binaryAnnotations()).isEmpty();
  }

  @Test void processAnnotationRecord_address_skipWrongKey() {
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

  @Test void endpoint_justIpv4() {
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
