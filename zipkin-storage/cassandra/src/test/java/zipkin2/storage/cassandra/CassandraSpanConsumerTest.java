/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.AggregateCall;
import zipkin2.storage.cassandra.internal.call.InsertEntry;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;

@ExtendWith(MockitoExtension.class)
class CassandraSpanConsumerTest {
  @Mock CqlSession session;
  Schema.Metadata metadata = new Schema.Metadata(true, true);
  CassandraSpanConsumer consumer;

  @BeforeEach void setup() {
    consumer = spanConsumer(CassandraStorage.newBuilder());
  }

  Span spanWithoutAnnotationsOrTags =
    Span.newBuilder()
      .traceId("a")
      .id("1")
      .name("get")
      .localEndpoint(FRONTEND)
      .timestamp(TODAY * 1000L)
      .duration(207000L)
      .build();

  @Test void emptyInput_emptyCall() {
    Call<Void> call = consumer.accept(List.of());
    assertThat(call).hasSameClassAs(Call.create(null));
  }

  @Test void doesntSetTraceIdHigh_128() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28")
      .build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple(null, span.traceId()));
  }

  @Test void doesntSetTraceIdHigh_64() {
    Span span = spanWithoutAnnotationsOrTags;

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple(null, span.traceId()));
  }

  @Test void strictTraceIdFalse_setsTraceIdHigh() {
    consumer = spanConsumer(CassandraStorage.newBuilder().strictTraceId(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28")
      .build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertSpan)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(tuple("77fcac3d4c5be8d2", "a037812820c65f28"));
  }

  @Test void serviceSpanKeys() {
    Span span = spanWithoutAnnotationsOrTags;

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertEntry)
      .extracting("input")
      .containsExactly(entry(FRONTEND.serviceName(), span.name()));
  }

  @Test void serviceRemoteServiceKeys_addsRemoteServiceName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertEntry)
      .extracting("input")
      .containsExactly(
        entry(FRONTEND.serviceName(), span.name()),
        entry(FRONTEND.serviceName(), BACKEND.serviceName())
      );
  }

  @Test void serviceRemoteServiceKeys_skipsRemoteServiceNameWhenNoLocalService() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .localEndpoint(null)
      .remoteEndpoint(BACKEND).build();

    Call<Void> call = consumer.accept(List.of(span));

    assertThat(call).isInstanceOf(InsertSpan.class);
  }

  @Test void serviceSpanKeys_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    assertThat(consumer.accept(List.of(span)))
      .isInstanceOf(ResultSetFutureCall.class);
  }

  /**
   * To allow lookups w/o a span name, we index "". "" is used instead of null to avoid creating
   * tombstones.
   */
  @Test void traceByServiceSpan_indexesLocalServiceNameAndEmptySpanName() {
    Span span = spanWithoutAnnotationsOrTags;

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(
        tuple(FRONTEND.serviceName(), span.name()), tuple(FRONTEND.serviceName(), ""));
  }

  @Test void traceByServiceSpan_indexesDurationInMillis() {
    Span span = spanWithoutAnnotationsOrTags;

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.duration")
      .containsOnly(span.durationAsLong() / 1000L);
  }

  @Test void traceByServiceSpan_indexesDurationMinimumZero() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().duration(12L).build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.duration")
      .containsOnly(0L);
  }

  @Test void traceByServiceSpan_skipsOnNoTimestamp() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().timestamp(null).build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .isEmpty();
  }

  @Test void traceByServiceSpan_doesntIndexRemoteService() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .hasSize(2)
      .extracting("input.service")
      .doesNotContain(BACKEND.serviceName());
  }

  @Test void traceByServiceSpan_appendsEmptyWhenNoName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    AggregateCall<?, Void> call = (AggregateCall<?, Void>) consumer.accept(List.of(span));
    assertThat(call.delegate())
      .filteredOn(c -> c instanceof InsertTraceByServiceSpan)
      .extracting("input.service", "input.span")
      .containsExactly(tuple(FRONTEND.serviceName(), ""));
  }

  @Test void traceByServiceSpan_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    assertThat(consumer.accept(List.of(span)))
      .isInstanceOf(ResultSetFutureCall.class);
  }

  @Test void searchDisabled_doesntIndex() {
    consumer = spanConsumer(CassandraStorage.newBuilder().searchEnabled(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .addAnnotation(TODAY * 1000L, "annotation")
      .putTag("foo", "bar")
      .duration(10000L)
      .build();

    assertThat(consumer.accept(List.of(span)))
      .extracting("input.annotation_query")
      .satisfies(q -> assertThat(q).isNull());
  }

  @Test void doesntIndexWhenOnlyIncludesTimestamp() {
    Span span = Span.newBuilder().traceId("a").id("1").timestamp(TODAY * 1000L).build();

    assertThat(consumer.accept(List.of(span)))
      .isInstanceOf(ResultSetFutureCall.class);
  }

  CassandraSpanConsumer spanConsumer(CassandraStorage.Builder builder) {
    return new CassandraSpanConsumer(session, metadata, builder.strictTraceId,
      builder.searchEnabled, builder.autocompleteKeys, builder.autocompleteTtl,
      builder.autocompleteCardinality);
  }
}
