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
package zipkin.storage.cassandra3.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import java.io.IOException;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.slf4j.LoggerFactory;
import zipkin.internal.Util;
import zipkin.storage.cassandra3.Cassandra3Storage;
import zipkin.storage.cassandra3.InternalForTests;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.storage.SpanConsumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin2.TestObjects.FRONTEND;

abstract class CassandraSpanConsumerTest {

  private final Appender mockAppender = mock(Appender.class);

  protected abstract Cassandra3Storage storage();

  @Before
  public void clear() {
    InternalForTests.clear(storage());
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    when(mockAppender.getName()).thenReturn(CassandraSpanConsumerTest.class.getName());
    root.addAppender(mockAppender);
  }

  @After
  public void tearDown() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(mockAppender);
  }

  /**
   * {@link Span#duration} == 0 is likely to be a mistake, and coerces to null. It is not helpful to
   * index rows who have no duration.
   */
  @Test
  public void doesntIndexSpansMissingDuration() throws IOException {
    Span span = Span.newBuilder().traceId("1").id("1").name("get").duration(0L).build();

    accept(storage().spanConsumer(), span);

    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage())).isZero();
  }

  @Test
  public void logTimestampMissingOnClientSend() throws IOException {
    Span span = Span.newBuilder().traceId("1").parentId("1").id("2").name("query")
      .localEndpoint(FRONTEND)
      .kind(Span.Kind.CLIENT).build();
    accept(storage().spanConsumer(), span);
    verify(mockAppender).doAppend(considerSwitchStrategyLog());
  }

  @Test
  public void dontLogTimestampMissingOnMidTierServerSpan() throws IOException {
    Span span = TestObjects.CLIENT_SPAN;
    accept(storage().spanConsumer(), span);
    verify(mockAppender, never()).doAppend(considerSwitchStrategyLog());
  }

  private static Object considerSwitchStrategyLog() {
    return argThat(new ArgumentMatcher<LoggingEvent>() {
      @Override public boolean matches(Object argument) {
        return ((LoggingEvent) argument).getFormattedMessage()
          .contains(
            "If this happens a lot consider switching back to SizeTieredCompactionStrategy");
      }
    });
  }

  /**
   * Simulates a trace with a step pattern, where each span starts a millisecond after the prior
   * one. The consumer code optimizes index inserts to only represent the interval represented by
   * the trace as opposed to each individual timestamp.
   */
  @Test
  public void skipsRedundantIndexingInATrace() throws IOException {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build();

    IntStream.range(0, 100).forEach(i -> {
      trace[i + 1] = Span.newBuilder()
        .traceId(trace[0].traceId())
        .parentId(trace[0].id())
        .id(Util.toLowerHex(i))
        .name("get")
        .kind(Span.Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .timestamp(
          trace[0].timestamp() + i * 1000) // all peer span timestamps happen a millisecond later
        .duration(10L)
        .build();
    });

    accept(storage().spanConsumer(), trace);
    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(4L);
    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(4L);

    // sanity check base case
    clear();

    accept(InternalForTests.withoutStrictTraceId(storage()), trace);

    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(201L);
    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(201L);
  }

  void accept(SpanConsumer consumer, Span... spans) throws IOException {
    consumer.accept(asList(spans)).execute();
  }
}
