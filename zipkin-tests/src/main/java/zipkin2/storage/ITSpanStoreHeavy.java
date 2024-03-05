/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import zipkin2.Span;

import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.spanBuilder;

/**
 * Base heavy tests for {@link SpanStore} implementations. Subtypes should create a connection to a
 * real backend, even if that backend is in-process.
 *
 * <p>As these tests create a lot of data, implementations may wish to isolate them from other
 * integration tests such as {@link ITSpanStore}
 */
public abstract class ITSpanStoreHeavy<T extends StorageComponent> extends ITStorage<T> {
  @Override protected boolean initializeStoragePerTest() {
    return true;
  }

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    // Defaults are fine.
  }

  // Bugs have happened in the past where trace limit was mistaken for span count.
  @Test protected void traceWithManySpans(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span = spanBuilder(testSuffix).build();

    int traceCount = 101;
    Span[] spans = new Span[traceCount];
    spans[0] = span;

    IntStream.range(1, spans.length).forEach(i ->
      spans[i] = span.toBuilder().parentId(span.id()).id(i)
        .timestamp((TODAY + i) * 1000).duration(10L)
        .build());

    accept(spans);

    assertGetTracesReturns(requestBuilder().build(), List.of(spans));
    assertGetTraceReturns(span.traceId(), List.of(spans));
  }

  /**
   * Formerly, a bug was present where cassandra didn't index more than bucket count traces per
   * millisecond. This stores a lot of spans to ensure indexes work under high-traffic scenarios.
   */
  @Test protected void getTraces_manyTraces(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span = spanBuilder(testSuffix).build();
    Map.Entry<String, String> tag = span.tags().entrySet().iterator().next();

    int traceCount = 1000;
    Span[] traces = new Span[traceCount];
    traces[0] = span;

    IntStream.range(1, traces.length).forEach(i ->
      traces[i] = spanBuilder(testSuffix)
        .timestamp((TODAY + i) * 1000).duration(10L)
        .build());

    accept(traces);

    assertGetTracesReturnsCount(requestBuilder().limit(traceCount).build(), traceCount);

    QueryRequest.Builder builder =
      requestBuilder().limit(traceCount).serviceName(span.localServiceName());

    assertGetTracesReturnsCount(
      builder.build(), traceCount);

    assertGetTracesReturnsCount(
      builder.remoteServiceName(span.remoteServiceName()).build(), traceCount);

    assertGetTracesReturnsCount(
      builder.spanName(span.name()).build(), traceCount);

    assertGetTracesReturnsCount(
      builder.parseAnnotationQuery(tag.getKey() + "=" + tag.getValue()).build(), traceCount);
  }
}
