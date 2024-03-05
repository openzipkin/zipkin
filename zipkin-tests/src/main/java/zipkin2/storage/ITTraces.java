/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import zipkin2.Span;

import static zipkin2.Span.Kind.SERVER;
import static zipkin2.TestObjects.newClientSpan;
import static zipkin2.TestObjects.newTraceId;
import static zipkin2.TestObjects.spanBuilder;

/**
 * Base test for {@link Traces}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITTraces<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    // Defaults are fine.
  }

  @Test protected void getTrace_returnsEmptyOnNotFound(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span clientSpan = newClientSpan(testSuffix);

    assertGetTraceReturnsEmpty(clientSpan.traceId());

    accept(clientSpan);

    assertGetTraceReturns(clientSpan);

    assertGetTraceReturnsEmpty(clientSpan.traceId().substring(16));
  }

  /** Prevents subtle bugs which can result in mixed-length traces from linking. */
  @Test protected void getTrace_differentiatesDebugFromShared(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span clientSpan = newClientSpan(testSuffix).toBuilder()
      .debug(true)
      .build();
    Span serverSpan = clientSpan.toBuilder().kind(SERVER)
      .debug(null).shared(true)
      .build();

    accept(clientSpan, serverSpan);

    // assertGetTraceReturns does recursive comparison
    assertGetTraceReturns(clientSpan.traceId(), List.of(clientSpan, serverSpan));
  }

  @Test protected void getTraces_onlyReturnsTracesThatMatch(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span1 = spanBuilder(testSuffix).build(), span2 = spanBuilder(testSuffix).build();
    List<String> traceIds = List.of(span1.traceId(), newTraceId());

    assertGetTracesReturnsEmpty(traceIds);

    accept(span1, span2);

    assertGetTracesReturns(traceIds, List.of(span1));

    List<String> shortTraceIds =
      traceIds.stream().map(t -> t.substring(16)).collect(Collectors.toList());
    assertGetTracesReturnsEmpty(shortTraceIds);
  }

  /** Prevents subtle bugs which can result in mixed-length traces from linking. */
  @Test protected void getTraces_differentiatesDebugFromShared(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span clientSpan = newClientSpan(testSuffix).toBuilder()
      .debug(true)
      .build();
    Span serverSpan = clientSpan.toBuilder().kind(SERVER)
      .debug(null).shared(true)
      .build();

    accept(clientSpan, serverSpan);

    // assertGetTracesReturns does recursive comparison
    assertGetTracesReturns(List.of(clientSpan.traceId()), List.of(clientSpan, serverSpan));
  }

  @Test protected void getTraces_returnsEmptyOnNotFound(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span1 = spanBuilder(testSuffix).build(), span2 = spanBuilder(testSuffix).build();
    List<String> traceIds = List.of(span1.traceId(), span2.traceId());

    assertGetTracesReturnsEmpty(traceIds);

    accept(span1, span2);

    assertGetTracesReturns(traceIds, List.of(span1), List.of(span2));

    List<String> shortTraceIds =
      traceIds.stream().map(t -> t.substring(16)).collect(Collectors.toList());
    assertGetTracesReturnsEmpty(shortTraceIds);
  }

  /**
   * Ideally, storage backends can deduplicate identical documents as this will prevent some
   * analysis problems such as double-counting dependency links or other statistics. While this test
   * exists, it is known not all backends will be able to cheaply make it pass. In other words, it
   * is optional.
   */
  @Test protected void getTrace_deduplicates(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span = spanBuilder(testSuffix).build();

    // simulate a re-processed message
    accept(span);
    accept(span);

    assertGetTraceReturns(span);
  }
}
