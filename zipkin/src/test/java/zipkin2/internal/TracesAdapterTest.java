/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.TestObjects;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;

class TracesAdapterTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  TracesAdapter adapter = new TracesAdapter(storage);

  /**
   * The contract for {@link zipkin2.storage.SpanStore#getTrace(java.lang.String)} is to return
   * empty on not found. This ensures a list of results aren't padded with empty ones.
   */
  @Test void getTraces_doesntReturnEmptyElements() throws Exception {
    storage.accept(TestObjects.TRACE).execute();

    assertThat(adapter.getTraces(List.of()).execute())
      .isEmpty();

    assertThat(adapter.getTraces(List.of("1")).execute())
      .isEmpty();

    assertThat(adapter.getTraces(List.of("1", "2")).execute())
      .isEmpty();

    assertThat(adapter.getTraces(List.of("1", TestObjects.TRACE.get(0).traceId(), "3")).execute())
      .containsExactly(TestObjects.TRACE);
  }
}
