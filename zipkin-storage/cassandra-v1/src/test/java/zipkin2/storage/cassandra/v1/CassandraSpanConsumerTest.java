/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.ProtocolVersion;
import com.google.common.cache.CacheBuilderSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.Test;
import org.mockito.Mockito;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;

public class CassandraSpanConsumerTest {
  CassandraSpanConsumer consumer = spanConsumer(CassandraStorage.newBuilder());

  Span spanWithoutAnnotationsOrTags =
    Span.newBuilder()
      .traceId("a")
      .id("1")
      .name("get")
      .localEndpoint(FRONTEND)
      .timestamp(TODAY * 1000L)
      .duration(207000L)
      .build();

  @Test public void emptyInput_emptyCall() {
    Call<Void> call = consumer.accept(Collections.emptyList());
    assertThat(call).hasSameClassAs(Call.create(null));
  }

  @Test public void doesntIndexWhenOnlyIncludesTimestamp() {
    Span span = Span.newBuilder().traceId("a").id("1").timestamp(TODAY * 1000L).build();

    assertThat(consumer.accept(singletonList(span)))
      .isInstanceOf(ResultSetFutureCall.class);
  }

  @Test public void doesntIndexWhenMissingLocalServiceName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().localEndpoint(null).build();

    assertThat(consumer.accept(singletonList(span)))
      .isInstanceOf(ResultSetFutureCall.class);
  }

  @Test public void indexesLocalServiceNameAndSpanName() {
    Span span = spanWithoutAnnotationsOrTags;

    Call<Void> call = consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof Indexer.IndexCall)
      .extracting("input.partitionKey")
      .containsExactly("frontend", "frontend.get");
  }

  @Test public void searchDisabled_doesntIndex() {
    consumer = spanConsumer(CassandraStorage.newBuilder().searchEnabled(false));

    Span span = spanWithoutAnnotationsOrTags.toBuilder()
      .addAnnotation(TODAY * 1000L, "annotation")
      .putTag("foo", "bar")
      .duration(10000L)
      .build();

    assertThat(consumer.accept(singletonList(span)))
      .isInstanceOf(InsertTrace.class);
  }

  @Test public void doesntIndexWhenMissingTimestamp() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().timestamp(null).build();

    Call<Void> call = consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof Indexer.IndexCall)
      .extracting("input.partitionKey")
      .isEmpty();
  }

  @Test public void indexKeysBasedOnLocalServiceNotRemote() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().remoteEndpoint(BACKEND).build();

    Call<Void> call = consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof Indexer.IndexCall)
      .extracting("input.partitionKey")
      .containsExactly("frontend", "frontend.backend", "frontend.get");
  }

  @Test public void indexesServiceNameWhenNoSpanName() {
    Span span = spanWithoutAnnotationsOrTags.toBuilder().name(null).build();

    Call<Void> call = consumer.accept(singletonList(span));

    assertEnclosedCalls(call)
      .filteredOn(c -> c instanceof Indexer.IndexCall)
      .extracting("input.partitionKey")
      .containsExactly(FRONTEND.serviceName());
  }

  static AbstractListAssert<?, List<? extends Call<Void>>, Call<Void>, ObjectAssert<Call<Void>>>
  assertEnclosedCalls(Call<Void> call) {
    return assertThat(call)
      .extracting("calls")
      .flatExtracting(calls -> (Collection<Call<Void>>) calls);
  }

  static CassandraSpanConsumer spanConsumer(CassandraStorage.Builder builder) {
    CassandraStorage storage =
      spy(builder.sessionFactory(mock(SessionFactory.class, Mockito.RETURNS_MOCKS)).build());
    doReturn(new Schema.Metadata(ProtocolVersion.V4, "", true, true, true))
      .when(storage).metadata();
    return new CassandraSpanConsumer(storage, CacheBuilderSpec.parse(""));
  }
}
