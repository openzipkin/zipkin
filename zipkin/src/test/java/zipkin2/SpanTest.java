/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin2.Span.normalizeTraceId;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;

class SpanTest {
  Span base = Span.newBuilder().traceId("1").id("1").localEndpoint(FRONTEND).build();
  Span oneOfEach = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("1")
    .id("2")
    .name("get")
    .kind(Span.Kind.SERVER)
    .localEndpoint(BACKEND)
    .remoteEndpoint(FRONTEND)
    .timestamp(1)
    .duration(3)
    .addAnnotation(2, "foo")
    .putTag("http.path", "/api")
    .shared(true)
    .debug(true)
    .build();

  @Test void traceIdString() {
    Span with128BitId = base.toBuilder()
      .traceId("463ac35c9f6413ad48485a3953bb6124")
      .name("foo").build();

    assertThat(with128BitId.traceId())
      .isEqualTo("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test void localEndpoint_emptyToNull() {
    assertThat(base.toBuilder().localEndpoint(Endpoint.newBuilder().build()).localEndpoint)
      .isNull();
  }

  @Test void remoteEndpoint_emptyToNull() {
    assertThat(base.toBuilder().remoteEndpoint(Endpoint.newBuilder().build()).remoteEndpoint)
      .isNull();
  }

  @Test void localServiceName() {
    assertThat(base.toBuilder().localEndpoint(null).build().localServiceName())
      .isNull();
    assertThat(base.toBuilder().localEndpoint(FRONTEND).build().localServiceName())
      .isEqualTo(FRONTEND.serviceName);
  }

  @Test void remoteServiceName() {
    assertThat(base.toBuilder().remoteEndpoint(null).build().remoteServiceName())
      .isNull();
    assertThat(base.toBuilder().remoteEndpoint(BACKEND).build().remoteServiceName())
      .isEqualTo(BACKEND.serviceName);
  }

  @Test void spanNamesLowercase() {
    assertThat(base.toBuilder().name("GET").build().name())
      .isEqualTo("get");
  }

  @Test void annotationsSortByTimestamp() {
    Span span = base.toBuilder()
      .addAnnotation(2L, "foo")
      .addAnnotation(1L, "foo")
      .build();

    // note: annotations don't also have endpoints, as it is implicit to Span.localEndpoint
    assertThat(span.annotations()).containsExactly(
      Annotation.create(1L, "foo"),
      Annotation.create(2L, "foo")
    );
  }

  @Test void annotationsDedupe() {
    Span span = base.toBuilder()
      .addAnnotation(2L, "foo")
      .addAnnotation(2L, "foo")
      .addAnnotation(1L, "foo")
      .addAnnotation(2L, "foo")
      .addAnnotation(3L, "foo")
      .build();

    assertThat(span.annotations()).containsExactly(
      Annotation.create(1L, "foo"),
      Annotation.create(2L, "foo"),
      Annotation.create(3L, "foo")
    );
  }

  @Test void putTagOverwritesValue() {
    Span span = base.toBuilder()
      .putTag("foo", "bar")
      .putTag("foo", "qux")
      .build();

    assertThat(span.tags()).containsExactly(
      entry("foo", "qux")
    );
  }

  @Test void builder_canUnsetParent() {
    Span withParent = base.toBuilder().parentId("3").build();

    assertThat(withParent.toBuilder().parentId(null).build().parentId())
      .isNull();
  }

  @Test void clone_differentCollections() {
    Span.Builder builder = base.toBuilder()
      .addAnnotation(1L, "foo")
      .putTag("foo", "qux");

    Span.Builder builder2 = builder.clone()
      .addAnnotation(2L, "foo")
      .putTag("foo", "bar");

    assertThat(builder.build()).isEqualTo(base.toBuilder()
      .addAnnotation(1L, "foo")
      .putTag("foo", "qux")
      .build()
    );

    assertThat(builder2.build()).isEqualTo(base.toBuilder()
      .addAnnotation(1L, "foo")
      .addAnnotation(2L, "foo")
      .putTag("foo", "bar")
      .build()
    );
  }

  /** Catches common error when zero is passed instead of null for a timestamp */
  @Test void coercesZeroTimestampsToNull() {
    Span span = base.toBuilder()
      .timestamp(0L)
      .duration(0L)
      .build();

    assertThat(span.timestamp())
      .isNull();
    assertThat(span.duration())
      .isNull();
  }

  @Test void canUsePrimitiveOverloads() {
    Span primitives = base.toBuilder()
      .timestamp(1L)
      .duration(1L)
      .shared(true)
      .debug(true)
      .build();

    Span objects = base.toBuilder()
      .timestamp(Long.valueOf(1L))
      .duration(Long.valueOf(1L))
      .shared(Boolean.TRUE)
      .debug(Boolean.TRUE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
  }

  @Test void debug_canUnset() {
    assertThat(base.toBuilder().debug(true).debug(null).build().debug())
      .isNull();
  }

  @Test void debug_canDisable() {
    assertThat(base.toBuilder().debug(true).debug(false).build().debug())
      .isFalse();
  }

  @Test void shared_canUnset() {
    assertThat(base.toBuilder().shared(true).shared(null).build().shared())
      .isNull();
  }

  @Test void shared_canDisable() {
    assertThat(base.toBuilder().shared(true).shared(false).build().shared())
      .isFalse();
  }

  @Test void nullToZeroOrFalse() {
    Span nulls = base.toBuilder()
      .timestamp(null)
      .duration(null)
      .build();

    Span zeros = base.toBuilder()
      .timestamp(0L)
      .duration(0L)
      .build();

    assertThat(nulls)
      .isEqualToComparingFieldByField(zeros);
  }

  @Test void builder_clear() {
    assertThat(oneOfEach.toBuilder().clear().traceId("a").id("a").build())
      .isEqualToComparingFieldByField(Span.newBuilder().traceId("a").id("a").build());
  }

  @Test void builder_clone() {
    Span.Builder builder = oneOfEach.toBuilder();
    assertThat(builder.clone())
      .isNotSameAs(builder)
      .isEqualToComparingFieldByField(builder);
  }

  @Test void builder_merge_redundant() {
    Span merged = oneOfEach.toBuilder().merge(oneOfEach).build();

    assertThat(merged).isEqualToComparingFieldByField(oneOfEach);
  }

  @Test void builder_merge_flags() {
    assertThat(Span.newBuilder().shared(true).merge(base.toBuilder().debug(true).build()).build())
      .isEqualToComparingFieldByField(base.toBuilder().shared(true).debug(true).build());
  }

  @Test void builder_merge_annotations() {
    Span merged = Span.newBuilder().merge(oneOfEach).build();

    assertThat(merged.annotations).containsExactlyElementsOf(oneOfEach.annotations);
  }

  @Test void builder_merge_annotations_concat() {
    Span merged = Span.newBuilder().addAnnotation(1, "a").addAnnotation(1, "b")
      .merge(base.toBuilder().addAnnotation(1, "b").addAnnotation(1, "c").build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().addAnnotation(1, "a").addAnnotation(1, "b").addAnnotation(1, "c").build()
    );
  }

  @Test void builder_merge_tags() {
    Span merged = Span.newBuilder().merge(oneOfEach).build();

    assertThat(merged.tags).containsAllEntriesOf(oneOfEach.tags);
  }

  @Test void builder_merge_tags_concat() {
    Span merged = Span.newBuilder().putTag("1", "a").putTag("2", "a")
      .merge(base.toBuilder().putTag("2", "a").putTag("3", "a").build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().putTag("1", "a").putTag("2", "a").putTag("3", "a").build()
    );
  }

  @Test void builder_merge_localEndpoint() {
    Span merged = Span.newBuilder()
      .merge(base.toBuilder().localEndpoint(FRONTEND).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().localEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_localEndpoint_redundant() {
    Span merged = Span.newBuilder().localEndpoint(FRONTEND)
      .merge(base.toBuilder().localEndpoint(FRONTEND).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().localEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_localEndpoint_merge() {
    Span merged = Span.newBuilder().localEndpoint(Endpoint.newBuilder().serviceName("a").build())
      .merge(
        base.toBuilder().localEndpoint(Endpoint.newBuilder().ip("192.168.99.101").build()).build())
      .merge(
        base.toBuilder().localEndpoint(Endpoint.newBuilder().ip("2001:db8::c001").build()).build())
      .merge(
        base.toBuilder().localEndpoint(Endpoint.newBuilder().port(80).build()).build())
      .build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().localEndpoint(Endpoint.newBuilder()
        .serviceName("a")
        .ip("192.168.99.101")
        .ip("2001:db8::c001")
        .port(80)
        .build()
      ).build()
    );
  }

  @Test void builder_merge_localEndpoint_null() {
    Span merged = Span.newBuilder().localEndpoint(FRONTEND)
      .merge(Span.newBuilder().traceId(base.traceId()).id(base.id()).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      Span.newBuilder().traceId(base.traceId()).id(base.id()).localEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_remoteEndpoint_null() {
    Span merged = Span.newBuilder().remoteEndpoint(FRONTEND)
      .merge(Span.newBuilder().traceId(base.traceId()).id(base.id()).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      Span.newBuilder().traceId(base.traceId()).id(base.id()).remoteEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_remoteEndpoint() {
    Span merged = Span.newBuilder()
      .merge(base.toBuilder().remoteEndpoint(FRONTEND).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().remoteEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_remoteEndpoint_redundant() {
    Span merged = Span.newBuilder().remoteEndpoint(FRONTEND)
      .merge(base.toBuilder().remoteEndpoint(FRONTEND).build()).build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().remoteEndpoint(FRONTEND).build()
    );
  }

  @Test void builder_merge_remoteEndpoint_merge() {
    Span merged = Span.newBuilder().remoteEndpoint(Endpoint.newBuilder().serviceName("a").build())
      .merge(
        base.toBuilder().remoteEndpoint(Endpoint.newBuilder().ip("192.168.99.101").build()).build())
      .merge(
        base.toBuilder().remoteEndpoint(Endpoint.newBuilder().ip("2001:db8::c001").build()).build())
      .merge(
        base.toBuilder().remoteEndpoint(Endpoint.newBuilder().port(80).build()).build())
      .build();

    assertThat(merged).isEqualToComparingFieldByField(
      base.toBuilder().remoteEndpoint(Endpoint.newBuilder()
        .serviceName("a")
        .ip("192.168.99.101")
        .ip("2001:db8::c001")
        .port(80)
        .build()
      ).build()
    );
  }

  @Test void toString_isJson() {
    assertThat(base.toString()).hasToString(
      "{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000001\",\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}"
    );
  }

  /** Test serializable as used in spark jobs. Careful to include all non-standard fields */
  @Test void serialization() throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    Span span = base.toBuilder()
      .addAnnotation(1L, "foo")
      .build();

    new ObjectOutputStream(buffer).writeObject(span);

    assertThat(new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray())).readObject())
      .isEqualTo(span);
  }

  @Test void traceIdFromLong() {
    assertThat(base.toBuilder().traceId(0L, 12345678L).build().traceId())
      .isEqualTo("0000000000bc614e");
  }

  @Test void traceIdFromLong_128() {
    assertThat(base.toBuilder().traceId(1234L, 5678L).build().traceId())
      .isEqualTo("00000000000004d2000000000000162e");
  }

  /** Some tools like rsocket redundantly pass high bits as zero. */
  @Test void normalizeTraceId_truncates64BitZeroPrefix() {
    assertThat(normalizeTraceId("0000000000000000000000000000162e"))
      .isEqualTo("000000000000162e");
  }

  @Test void normalizeTraceId_padsTo64() {
    assertThat(normalizeTraceId("162e"))
      .isEqualTo("000000000000162e");
  }

  @Test void normalizeTraceId_padsTo128() {
    assertThat(normalizeTraceId("4d2000000000000162e"))
      .isEqualTo("00000000000004d2000000000000162e");
  }

  @Test void normalizeTraceId_badCharacters() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      normalizeTraceId("000-0000000004d20000000ss000162e");
    });
  }

  @Test void traceIdFromLong_invalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().traceId(0, 0);
    });
  }

  @Test void parentIdFromLong() {
    assertThat(base.toBuilder().parentId(3405691582L).build().parentId())
      .isEqualTo("00000000cafebabe");
  }

  @Test void parentIdFromLong_zeroSameAsNull() {
    assertThat(base.toBuilder().parentId(0L).build().parentId())
      .isNull();
    assertThat(base.toBuilder().parentId("0").build().parentId())
      .isNull();
  }

  /** Prevents processing tools from looping */
  @Test void parentId_sameAsIdCoerseToNull() {
    assertThat(base.toBuilder().parentId(base.id).build().parentId())
      .isNull();
  }

  @Test void removesSharedFlagFromClientSpans() {
    assertThat(base.toBuilder().kind(Span.Kind.CLIENT).build().shared())
      .isNull();
  }

  @Test void idFromLong() {
    assertThat(base.toBuilder().id(3405691582L).build().id())
      .isEqualTo("00000000cafebabe");
  }

  @Test void idFromLong_minValue() {
    assertThat(base.toBuilder().id(Long.MAX_VALUE).build().id())
      .isEqualTo("7fffffffffffffff");
  }

  @Test void idFromLong_invalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().id(0);
    });
  }

  @Test void id_emptyInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().id("");
    });
  }

  @Test void id_zerosInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().id("0000000000000000");
    });
  }

  @Test void parentId_emptyInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().parentId("");
    });
  }

  @Test void traceId_emptyInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().traceId("");
    });
  }

  @Test void traceId_zerosInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().traceId("0000000000000000");
    });
  }

  @Test void traceId_uuidInvalid() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      base.toBuilder().traceId(UUID.randomUUID().toString());
    });
  }
}
