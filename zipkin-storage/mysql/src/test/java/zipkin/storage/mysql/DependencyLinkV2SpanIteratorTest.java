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
package zipkin.storage.mysql;

import org.jooq.Record;
import org.jooq.Record7;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Test;
import zipkin.BinaryAnnotation.Type;
import zipkin.Constants;
import zipkin.internal.PeekingIterator;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

public class DependencyLinkV2SpanIteratorTest {
  Long traceIdHigh = null;
  long traceId = 1L;
  Long parentId = null;
  long spanId = 1L;

  /** You cannot make a dependency link unless you know the the local or peer endpoint. */
  @Test public void whenNoServiceLabelsExist_kindIsUnknown() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, null)
    );

    Span span = iterator.next();
    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  @Test public void whenOnlyAddressLabelsExist_kindIsNull() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", Type.BOOL.value, "service2")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localServiceName()).isEqualTo("service1");
    assertThat(span.remoteServiceName()).isEqualTo("service2");
  }

  /** The linker is biased towards server spans, or client spans that know the peer localEndpoint(). */
  @Test public void whenServerLabelsAreMissing_kindIsUnknownAndLabelsAreCleared() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service1")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  /** {@link Constants#SERVER_RECV} is only applied when the local span is acting as a server */
  @Test public void whenSrServiceExists_kindIsServer() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service");
    assertThat(span.remoteEndpoint()).isNull();
  }

  @Test public void errorAnnotationIgnored() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "error", -1, "service")
    );
    Span span = iterator.next();

    assertThat(span.tags()).isEmpty();
    assertThat(span.annotations()).isEmpty();
  }

  @Test public void errorTagAdded() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "error", Type.STRING.value, "foo")
    );
    Span span = iterator.next();

    assertThat(span.tags()).containsOnly(
      entry("error", "")
    );
  }

  /**
   * {@link Constants#CLIENT_ADDR} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test public void whenSrAndCaServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service2")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service2");
    assertThat(span.remoteServiceName()).isEqualTo("service1");
  }

  /**
   * {@link Constants#CLIENT_SEND} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test public void whenSrAndCsServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service2")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service2");
    assertThat(span.remoteServiceName()).isEqualTo("service1");
  }

  /** {@link Constants#CLIENT_ADDR} is more authoritative than {@link Constants#CLIENT_SEND} */
  @Test public void whenCrAndCaServiceExists_caIsThePeer() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "foo"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service2")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service2");
    assertThat(span.remoteServiceName()).isEqualTo("service1");
  }

  /** Finagle labels two sides of the same socket "ca", Type.BOOL.value, "sa" with the local endpoint name */
  @Test public void specialCasesFinagleLocalSocketLabeling_client() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", Type.BOOL.value, "service")
    );
    Span span = iterator.next();

    // When there's no "sr" annotation, we assume it is a client.
    assertThat(span.kind()).isEqualTo(Span.Kind.CLIENT);
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteServiceName()).isEqualTo("service");
  }

  @Test public void specialCasesFinagleLocalSocketLabeling_server() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", Type.BOOL.value, "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "service")
    );
    Span span = iterator.next();

    // When there is an "sr" annotation, we know it is a server
    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service");
    assertThat(span.remoteEndpoint()).isNull();
  }

  /**
   * <p>Dependency linker works backwards: it is easier to treat a "cs" as a server span lacking its
   * caller, than a client span lacking its receiver.
   */
  @Test public void csWithoutSaIsServer() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, "service1")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.localServiceName()).isEqualTo("service1");
    assertThat(span.remoteEndpoint()).isNull();
  }

  /** Service links to empty string are confusing and offer no value. */
  @Test public void emptyToNull() {
    DependencyLinkV2SpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", Type.BOOL.value, ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", -1, ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", Type.BOOL.value, ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", -1, "")
    );
    Span span = iterator.next();

    assertThat(span.kind()).isNull();
    assertThat(span.localEndpoint()).isNull();
    assertThat(span.remoteEndpoint()).isNull();
  }

  static DependencyLinkV2SpanIterator iterator(Record... records) {
    return new DependencyLinkV2SpanIterator(
      new PeekingIterator<>(asList(records).iterator()), records[0].get(ZIPKIN_SPANS.TRACE_ID_HIGH),
      records[0].get(ZIPKIN_SPANS.TRACE_ID)
    );
  }

  static Record7<Long, Long, Long, Long, String, Integer, String> newRecord() {
    return DSL.using(SQLDialect.MYSQL)
      .newRecord(ZIPKIN_SPANS.TRACE_ID_HIGH, ZIPKIN_SPANS.TRACE_ID, ZIPKIN_SPANS.PARENT_ID,
        ZIPKIN_SPANS.ID, ZIPKIN_ANNOTATIONS.A_KEY, ZIPKIN_ANNOTATIONS.A_TYPE,
        ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }
}
