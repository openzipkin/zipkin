/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.List;
import org.jooq.Record;
import org.jooq.Record7;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations;
import zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin2.storage.mysql.v1.Schema.maybeGet;
import static zipkin2.v1.V1BinaryAnnotation.TYPE_BOOLEAN;
import static zipkin2.v1.V1BinaryAnnotation.TYPE_STRING;

class DependencyLinkV2SpanIteratorTest {
  Long traceIdHigh = null;
  long traceId = 1L;
  Long parentId = null;
  long spanId = 1L;

  /** You cannot make a dependency link unless you know the the local or peer endpoint. */
  @Test void whenNoServiceLabelsExist_kindIsUnknown() {
    DependencyLinkV2SpanIterator iterator =
        iterator(newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, null));

    Span span = iterator.next();
    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  @Test void whenOnlyAddressLabelsExist_kindIsNull() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "s1"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", TYPE_BOOLEAN, "s2"));
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localServiceName()).isEqualTo("s1");
    assertThat(span.remoteServiceName()).isEqualTo("s2");
  }

  /**
   * The linker is biased towards server spans, or client spans that know the peer localEndpoint().
   */
  @Test void whenServerLabelsAreMissing_kindIsUnknownAndLabelsAreCleared() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "s1"));
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  /** "sr" is only applied when the local span is acting as a server */
  @Test void whenSrServiceExists_kindIsServer() {
    DependencyLinkV2SpanIterator iterator =
        iterator(newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service"));
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service");
    assertThat(span.remoteEndpoint()).isNull();
  }

  @Test void errorAnnotationIgnored() {
    DependencyLinkV2SpanIterator iterator =
      iterator(
        newRecord().values(traceIdHigh, traceId, parentId, spanId, "error", -1, "service"));
    Span span = iterator.next();

    assertThat(span.tags()).isEmpty();
    assertThat(span.annotations()).isEmpty();
  }

  @Test void errorTagAdded() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord()
                .values(traceIdHigh, traceId, parentId, spanId, "error", TYPE_STRING, "foo"));
    Span span = iterator.next();

    assertThat(span.tags()).containsOnly(entry("error", ""));
  }

  /** "ca" indicates the peer, which is a client in the case of a server span */
  @Test void whenSrAndCaServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "s1"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "s2"));
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("s2");
    assertThat(span.remoteServiceName()).isEqualTo("s1");
  }

  /** "cs" indicates the peer, which is a client in the case of a server span */
  @Test void whenSrAndCsServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "s1"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "s2"));
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("s2");
    assertThat(span.remoteServiceName()).isEqualTo("s1");
  }

  /** "ca" is more authoritative than "cs" */
  @Test void whenCrAndCaServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "foo"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "s1"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "s2"));
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("s2");
    assertThat(span.remoteServiceName()).isEqualTo("s1");
  }

  /**
   * Finagle labels two sides of the same socket "ca", V1BinaryAnnotation.TYPE_BOOLEAN, "sa" with
   * the local endpoint name
   */
  @Test void specialCasesFinagleLocalSocketLabeling_client() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "service"),
            newRecord()
                .values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "service"),
            newRecord()
                .values(traceIdHigh, traceId, parentId, spanId, "sa", TYPE_BOOLEAN, "service"));
    Span span = iterator.next();

    // When there's no "sr" annotation, we assume it is a client.
    assertThat(span.kind()).isEqualTo(Span.Kind.CLIENT);
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteServiceName()).isEqualTo("service");
  }

  @Test void specialCasesFinagleLocalSocketLabeling_server() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord()
                .values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, "service"),
            newRecord()
                .values(traceIdHigh, traceId, parentId, spanId, "sa", TYPE_BOOLEAN, "service"),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service"));
    Span span = iterator.next();

    // When there is an "sr" annotation, we know it is a server
    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service");
    assertThat(span.remoteEndpoint()).isNull();
  }

  /**
   * Dependency linker works backwards: it is easier to treat a "cs" as a server span lacking its
   * caller, than a client span lacking its receiver.
   */
  @Test void csWithoutSaIsServer() {
    DependencyLinkV2SpanIterator iterator =
        iterator(newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "s1"));
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("s1");
    assertThat(span.remoteEndpoint()).isNull();
  }

  /**
   * Service links to empty string are confusing and offer no value.
   */
  @Test void emptyToNull() {
    DependencyLinkV2SpanIterator iterator =
        iterator(
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", TYPE_BOOLEAN, ""),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, ""),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", TYPE_BOOLEAN, ""),
            newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, ""));
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  static DependencyLinkV2SpanIterator iterator(Record... records) {
    return new DependencyLinkV2SpanIterator(
        new PeekingIterator<>(List.of(records).iterator()),
        maybeGet(records[0], ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH, 0L),
        records[0].get(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID));
  }

  static Record7<Long, Long, Long, Long, String, Integer, String> newRecord() {
    return DSL.using(SQLDialect.MYSQL)
        .newRecord(
            ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH,
            ZipkinSpans.ZIPKIN_SPANS.TRACE_ID,
            ZipkinSpans.ZIPKIN_SPANS.PARENT_ID,
            ZipkinSpans.ZIPKIN_SPANS.ID,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_KEY,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_TYPE,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }
}
