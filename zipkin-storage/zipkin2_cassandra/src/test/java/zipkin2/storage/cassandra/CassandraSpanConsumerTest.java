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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    verify(call.statements.get(0)).setString("trace_id", span.traceId());
    verify(call.statements.get(0), never()).setString(eq("trace_id_high"), any());
  }

  @Test public void doesntSetTraceIdHigh_64() {
    Span span = spanWithoutAnnotationsOrTags;
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.statements).hasSize(3);

    verify(call.statements.get(0)).setString("trace_id", span.traceId());
    verify(call.statements.get(0), never()).setString(eq("trace_id_high"), any());
  }

  @Test public void strictTraceIdFalse_setsTraceIdHigh() {
    consumer = spanConsumer(CassandraStorage.newBuilder().strictTraceId(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .traceId("77fcac3d4c5be8d2a037812820c65f28").build();
    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));

    verify(call.statements.get(0)).setString("trace_id", "a037812820c65f28");
    verify(call.statements.get(0)).setString("id", span.id());
    verify(call.statements.get(0)).setString("trace_id_high", "77fcac3d4c5be8d2");
  }

  @Test public void serviceSpanKeys() {
    Span span = spanWithoutAnnotationsOrTags;

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.serviceSpanKeys).extracting(b -> b.key)
      .containsExactlyInAnyOrder("frontend෴get");
  }

  @Test public void serviceSpanKeys_addsRemoteServiceName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.serviceSpanKeys).extracting(b -> b.key)
      .containsExactlyInAnyOrder("frontend෴get", "backend෴get");
  }

  @Test public void serviceSpanKeys_appendsEmptyWhenNoName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.serviceSpanKeys).extracting(b -> b.key)
      .containsExactlyInAnyOrder("frontend෴");
  }

  @Test public void serviceSpanKeys_emptyWhenNoEndpoints() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    StoreSpansCall call = (StoreSpansCall) consumer.accept(singletonList(span));
    assertThat(call.serviceSpanKeys).isEmpty(); // no services to index
  }

  static CassandraSpanConsumer spanConsumer(CassandraStorage.Builder builder) {
    return new CassandraSpanConsumer(
      builder.sessionFactory(mock(CassandraStorage.SessionFactory.class, Mockito.RETURNS_MOCKS))
        .build()) {
      @Override BoundStatement bindWithName(PreparedStatement prepared,
        String name) { // overridable for tests
        return mock(BoundStatement.class, withSettings()
          .name(name)
          .defaultAnswer(InvocationOnMock::getMock)); // for chaining
      }
    };
  }
}
