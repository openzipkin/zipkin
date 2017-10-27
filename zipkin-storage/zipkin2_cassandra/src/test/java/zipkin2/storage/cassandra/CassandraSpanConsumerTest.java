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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import java.util.Collections;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.cassandra.CassandraSpanConsumer.StoreSpansCall;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;

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

    assertThat(call.calls.get(0))
      .isInstanceOf(InsertSpan.class)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(null, span.traceId());
  }

  @Test public void doesntSetTraceIdHigh_64() {
    Span span = spanWithoutAnnotationsOrTags;
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertThat(call.calls.get(0))
      .isInstanceOf(InsertSpan.class)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly(null, span.traceId());
  }

  @Test public void strictTraceIdFalse_setsTraceIdHigh() {
    consumer = spanConsumer(CassandraStorage.newBuilder().strictTraceId(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28").build();
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertThat(call.calls.get(0))
      .isInstanceOf(InsertSpan.class)
      .extracting("input.trace_id_high", "input.trace_id")
      .containsExactly("77fcac3d4c5be8d2", "a037812820c65f28");
  }

  @Test public void serviceSpanKeys() {
    Span span = spanWithoutAnnotationsOrTags;

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertThat(call.calls)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .hasSize(1)
      .flatExtracting("input.service", "input.span")
      .containsExactly("frontend", "get");
  }

  @Test public void serviceSpanKeys_addsRemoteServiceName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    assertThat(call.calls)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .hasSize(2)
      .flatExtracting("input.service", "input.span")
      .containsExactly("backend", "get", "frontend", "get");
  }

  @Test public void serviceSpanKeys_appendsEmptyWhenNoName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.calls)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .hasSize(1)
      .flatExtracting("input.service", "input.span")
      .containsExactly("frontend", "");
  }

  @Test public void serviceSpanKeys_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.calls)
      .filteredOn(c -> c instanceof InsertServiceSpan)
      .isEmpty();
  }

  static CassandraSpanConsumer spanConsumer(CassandraStorage.Builder builder) {
    return new CassandraSpanConsumer(
      builder.sessionFactory(mock(CassandraStorage.SessionFactory.class, Mockito.RETURNS_MOCKS))
        .build()) {
      @Override BoundStatement bind(PreparedStatement prepared) { // overridable for tests
        return mock(BoundStatement.class, withSettings()
          .defaultAnswer(InvocationOnMock::getMock)); // for chaining
      }
    };
  }
}
