/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.ResultSet;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.ObjectArrayAssert;
import org.junit.Test;
import org.mockito.Mockito;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.cassandra.CassandraSpanConsumer.StoreSpansCall;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;

public class CassandraSpanConsumerTest {
  CassandraSpanConsumer consumer = spanConsumer(CassandraStorage.newBuilder());

  Span spanWithoutAnnotationsOrTags = Span.newBuilder()
    .traceId("a")
    .id("1")
    .name("get")
    .localEndpoint(FRONTEND)
    .timestamp(1472470996199000L)
    .duration(207000L)
    .build();

  @Test public void emptyInput_emptyCall() {
    Call<Void> call = consumer.accept(Collections.emptyList());
    assertThat(call).hasSameClassAs(Call.create(null));
  }

  @Test public void doesntSetTraceIdHigh_128() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28").build();
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple(null, span.traceId()));
  }

  @Test public void doesntSetTraceIdHigh_64() {
    Span span = spanWithoutAnnotationsOrTags;
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple(null, span.traceId()));
  }

  @Test public void strictTraceIdFalse_setsTraceIdHigh() {
    consumer = spanConsumer(CassandraStorage.newBuilder().strictTraceId(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28").build();
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple("77fcac3d4c5be8d2", "a037812820c65f28"));
  }

  @Test public void serviceSpanKeys() {
    Span span = spanWithoutAnnotationsOrTags;

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(
        tuple(FRONTEND.serviceName(), span.name())
      );
  }

  @Test public void serviceSpanKeys_addsRemoteServiceName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(
        tuple(BACKEND.serviceName(), span.name()),
        tuple(FRONTEND.serviceName(), span.name())
      );
  }

  @Test public void serviceSpanKeys_appendsEmptyWhenNoName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(tuple(FRONTEND.serviceName(), ""));
  }

  @Test public void serviceSpanKeys_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .isEmpty();
  }

  /**
   * To allow lookups w/o a span name, we index "". "" is used instead of null to avoid creating
   * tombstones.
   */
  @Test public void traceByServiceSpan_indexesLocalServiceNameAndEmptySpanName() {
    Span span = spanWithoutAnnotationsOrTags;

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(
        tuple(FRONTEND.serviceName(), span.name()),
        tuple(FRONTEND.serviceName(), "")
      );
  }

  @Test public void traceByServiceSpan_indexesDurationInMillis() {
    Span span = spanWithoutAnnotationsOrTags;

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.duration")
      .containsOnly(span.duration() / 1000L);
  }

  @Test public void traceByServiceSpan_indexesDurationMinimumZero() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().duration(12L).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.duration")
      .containsOnly(0L);
  }

  @Test public void traceByServiceSpan_skipsOnNoTimestamp() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().timestamp(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .isEmpty();
  }

  @Test public void traceByServiceSpan_doesntIndexRemoteService() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .hasSize(2)
      .extracting("input.service")
      .doesNotContain(BACKEND.serviceName());
  }

  @Test public void traceByServiceSpan_appendsEmptyWhenNoName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(
        tuple(FRONTEND.serviceName(), "")
      );
  }

  @Test public void traceByServiceSpan_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .isEmpty();
  }

  @Test public void searchDisabled_doesntIndex() {
    consumer = spanConsumer(CassandraStorage.newBuilder().searchEnabled(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .addAnnotation(TODAY, "annotation")
      .putTag("foo", "bar")
      .duration(10000L).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .hasSize(1)
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.annotation_query")
      .allSatisfy(q -> assertThat(q).isNull());
  }

  static ObjectArrayAssert<Call<ResultSet>> assertEnclosedCalls(
    StoreSpansCall call) {
    return assertThat(call).extracting("calls")
      .flatExtracting(calls -> (List<Call<ResultSet>>) calls);
  }

  static CassandraSpanConsumer spanConsumer(CassandraStorage.Builder builder) {
    return new CassandraSpanConsumer(
      builder.sessionFactory(mock(CassandraStorage.SessionFactory.class, Mockito.RETURNS_MOCKS))
        .build()) {

    };
  }
}
