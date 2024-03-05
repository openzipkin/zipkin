/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.newClientSpan;
import static zipkin2.TestObjects.spanBuilder;

/**
 * Base test for when {@link StorageComponent.Builder#searchEnabled(boolean) searchEnabled ==
 * false}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITSearchEnabledFalse<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    storage.searchEnabled(false);
  }

  @Test protected void getTraces_indexDataReturnsNothing(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span clientSpan = newClientSpan(testSuffix);
    accept(clientSpan);

    assertGetTracesReturnsEmpty(
      requestBuilder().build());

    assertGetTracesReturnsEmpty(
      requestBuilder().serviceName(clientSpan.localServiceName()).build());

    assertGetTracesReturnsEmpty(
      requestBuilder().spanName(clientSpan.name()).build());

    assertGetTracesReturnsEmpty(
      requestBuilder().annotationQuery(clientSpan.tags()).build());

    assertGetTracesReturnsEmpty(
      requestBuilder().minDuration(clientSpan.duration()).build());
  }

  @Test protected void getServiceNames_isEmpty(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    accept(spanBuilder(testSuffix).build());

    assertThat(names().getServiceNames().execute()).isEmpty();
  }

  @Test protected void getRemoteServiceNames_isEmpty(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span = spanBuilder(testSuffix).build();

    accept(span);

    assertThat(names().getRemoteServiceNames(span.localServiceName()).execute()).isEmpty();
  }

  @Test protected void getSpanNames_isEmpty(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    Span span = spanBuilder(testSuffix).build();

    accept(span);

    assertThat(names().getSpanNames(span.localServiceName()).execute()).isEmpty();
  }
}
