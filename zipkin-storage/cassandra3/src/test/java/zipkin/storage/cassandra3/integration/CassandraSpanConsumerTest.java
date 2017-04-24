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
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import zipkin.Annotation;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.cassandra3.Cassandra3Storage;
import zipkin.storage.cassandra3.InternalForTests;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.TestObjects.APP_ENDPOINT;

abstract class CassandraSpanConsumerTest {

  private final Appender mockAppender = mock(Appender.class);

  abstract protected Cassandra3Storage storage();

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
  public void doesntIndexSpansMissingDuration() {
    Span span = Span.builder().traceId(1L).id(1L).name("get").duration(0L).build();

    accept(storage().asyncSpanConsumer(), span);

    assertThat(InternalForTests.rowCountForTraceByServiceSpan(storage())).isZero();
  }

  @Test
  public void logTimestampMissingOnClientSend() {
    Span span = Span.builder().traceId(1L).parentId(1L).id(2L).name("query")
      .addAnnotation(Annotation.create(0L, CLIENT_SEND, APP_ENDPOINT))
      .addAnnotation(Annotation.create(0L, CLIENT_RECV, APP_ENDPOINT)).build();
    accept(storage().asyncSpanConsumer(), span);
    verify(mockAppender).doAppend(considerSwitchStrategyLog());
  }

  @Test
  public void dontLogTimestampMissingOnMidTierServerSpan() {
    Span span = TestObjects.TRACE.get(0);
    accept(storage().asyncSpanConsumer(), span);
    verify(mockAppender, never()).doAppend(considerSwitchStrategyLog());
  }

  private static Object considerSwitchStrategyLog() {
    return argThat(argument -> ((LoggingEvent) argument).getFormattedMessage()
      .contains("If this happens a lot consider switching back to SizeTieredCompactionStrategy"));
  }

  /**
   * Simulates a trace with a step pattern, where each span starts a millisecond after the prior
   * one. The consumer code optimizes index inserts to only represent the interval represented by
   * the trace as opposed to each individual timestamp.
   */
  @Test
  public void skipsRedundantIndexingInATrace() {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.TRACE.get(0);

    IntStream.range(0, 100).forEach(i -> {
      Span s = TestObjects.TRACE.get(1);
      trace[i + 1] = s.toBuilder()
        .id(s.id + i)
        .timestamp(s.timestamp + i * 1000) // all peer span timestamps happen a millisecond later
        .annotations(s.annotations.stream()
          .map(a -> Annotation.create(a.timestamp + i * 1000, a.value, a.endpoint))
          .collect(toList()))
        .build();
    });

    accept(storage().asyncSpanConsumer(), trace);
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

  void accept(AsyncSpanConsumer consumer, Span... spans) {
    // Blocks until the callback completes to allow read-your-writes consistency during tests.
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    consumer.accept(asList(spans), captor);
    captor.get(); // block on result
  }
}
