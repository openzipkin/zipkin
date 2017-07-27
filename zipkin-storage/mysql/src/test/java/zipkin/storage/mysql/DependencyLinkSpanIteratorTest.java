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
import org.jooq.Record6;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Test;
import zipkin.Constants;
import zipkin.internal.DependencyLinkSpan;
import zipkin.internal.PeekingIterator;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

// TODO: this class temporarily uses reflection until zipkin2 span replaces DependencyLinkSpan
public class DependencyLinkSpanIteratorTest {
  Long traceIdHigh = null;
  long traceId = 1L;
  Long parentId = null;
  long spanId = 1L;

  /** You cannot make a dependency link unless you know the the local or peer endpoint. */
  @Test public void whenNoServiceLabelsExist_kindIsUnknown() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", null)
    );

    DependencyLinkSpan span = iterator.next();
    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("UNKNOWN");
    assertThat(span).extracting("service").containsNull();
    assertThat(span).extracting("peerService").containsNull();
  }

  @Test public void whenOnlyAddressLabelsExist_kindIsClient() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", "service2")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("CLIENT");
    assertThat(span).extracting("service").containsOnly("service1");
    assertThat(span).extracting("peerService").containsOnly("service2");
  }

  /** The linker is biased towards server spans, or client spans that know the peer localEndpoint(). */
  @Test public void whenServerLabelsAreMissing_kindIsUnknownAndLabelsAreCleared() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service1")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("UNKNOWN");
    assertThat(span).extracting("service").containsNull();
    assertThat(span).extracting("peerService").containsNull();
  }

  /** {@link Constants#SERVER_RECV} is only applied when the local span is acting as a server */
  @Test public void whenSrServiceExists_kindIsServer() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "service")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service");
    assertThat(span).extracting("peerService").containsNull();
  }

  /**
   * {@link Constants#CLIENT_ADDR} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test public void whenSrAndCaServiceExists_caIsThePeer() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "service2")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service2");
    assertThat(span).extracting("peerService").containsOnly("service1");
  }

  /**
   * {@link Constants#CLIENT_SEND} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test public void whenSrAndCsServiceExists_caIsThePeer() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "service2")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service2");
    assertThat(span).extracting("peerService").containsOnly("service1");
  }

  /** {@link Constants#CLIENT_ADDR} is more authoritative than {@link Constants#CLIENT_SEND} */
  @Test public void whenCrAndCaServiceExists_caIsThePeer() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", "foo"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service1"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "service2")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service2");
    assertThat(span).extracting("peerService").containsOnly("service1");
  }

  /** Finagle labels two sides of the same socket "ca", "sa" with the local endpoint name */
  @Test public void specialCasesFinagleLocalSocketLabeling_client() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", "service")
    );
    DependencyLinkSpan span = iterator.next();

    // When there's no "sr" annotation, we assume it is a client.
    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("CLIENT");
    assertThat(span).extracting("service").containsNull();
    assertThat(span).extracting("peerService").containsOnly("service");
  }

  @Test public void specialCasesFinagleLocalSocketLabeling_server() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", "service"),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "service")
    );
    DependencyLinkSpan span = iterator.next();

    // When there is an "sr" annotation, we know it is a server
    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service");
    assertThat(span).extracting("peerService").containsNull();
  }

  /**
   * <p>Dependency linker works backwards: it is easier to treat a "cs" as a server span lacking its
   * caller, than a client span lacking its receiver.
   */
  @Test public void csWithoutSaIsServer() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", "service1")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("SERVER");
    assertThat(span).extracting("service").containsOnly("service1");
    assertThat(span).extracting("peerService").containsNull();
  }

  /** Service links to empty string are confusing and offer no value. */
  @Test public void emptyToNull() {
    DependencyLinkSpanIterator iterator = iterator(
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "ca", ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "cs", ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sa", ""),
      newRecord().values(traceIdHigh, traceId, parentId, spanId, "sr", "")
    );
    DependencyLinkSpan span = iterator.next();

    assertThat(span).extracting("kind").extracting(Object::toString).containsOnly("UNKNOWN");
    assertThat(span).extracting("service").containsNull();
    assertThat(span).extracting("peerService").containsNull();
  }

  static DependencyLinkSpanIterator iterator(Record... records) {
    return new DependencyLinkSpanIterator(
      new PeekingIterator<>(asList(records).iterator()), records[0].get(ZIPKIN_SPANS.TRACE_ID_HIGH),
      records[0].get(ZIPKIN_SPANS.TRACE_ID)
    );
  }

  static Record6<Long, Long, Long, Long, String, String> newRecord() {
    return DSL.using(SQLDialect.MYSQL)
      .newRecord(ZIPKIN_SPANS.TRACE_ID_HIGH, ZIPKIN_SPANS.TRACE_ID, ZIPKIN_SPANS.PARENT_ID,
        ZIPKIN_SPANS.ID, ZIPKIN_ANNOTATIONS.A_KEY, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }
}
