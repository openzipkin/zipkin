/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

/**
 * Base test for {@link Traces}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITTraces<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    // Defaults are fine.
  }

  @Test protected void getTrace_returnsEmptyOnNotFound() throws IOException {
    assertThat(traces().getTrace(CLIENT_SPAN.traceId()).execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(traces().getTrace(CLIENT_SPAN.traceId()).execute())
      .containsExactly(CLIENT_SPAN);

    assertThat(traces().getTrace(CLIENT_SPAN.traceId().substring(16)).execute())
      .isEmpty();
  }


  @Test protected void getTraces_onlyReturnsTracesThatMatch() throws IOException {
    List<String> traceIds = asList(LOTS_OF_SPANS[0].traceId(), LOTS_OF_SPANS[1].traceId());

    assertThat(traces().getTraces(traceIds).execute())
      .isEmpty();

    accept(LOTS_OF_SPANS[0], LOTS_OF_SPANS[2]);

    assertThat(traces().getTraces(traceIds).execute())
      .containsOnly(asList(LOTS_OF_SPANS[0]));

    List<String> longTraceIds = traceIds.stream().map(t -> "a" + t).collect(Collectors.toList());
    assertThat(traces().getTraces(longTraceIds).execute())
      .isEmpty();
  }

  @Test protected void getTraces_returnsEmptyOnNotFound() throws IOException {
    List<String> traceIds = asList(LOTS_OF_SPANS[0].traceId(), LOTS_OF_SPANS[1].traceId());

    assertThat(traces().getTraces(traceIds).execute())
      .isEmpty();

    accept(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

    assertThat(traces().getTraces(traceIds).execute())
      .containsExactlyInAnyOrder(asList(LOTS_OF_SPANS[0]), asList(LOTS_OF_SPANS[1]));

    List<String> longTraceIds = traceIds.stream().map(t -> "a" + t).collect(Collectors.toList());
    assertThat(traces().getTraces(longTraceIds).execute())
      .isEmpty();
  }

  /**
   * Ideally, storage backends can deduplicate identical documents as this will prevent some
   * analysis problems such as double-counting dependency links or other statistics. While this test
   * exists, it is known not all backends will be able to cheaply make it pass. In other words, it
   * is optional.
   */
  @Test protected void getTrace_deduplicates() throws IOException {
    // simulate a re-processed message
    accept(LOTS_OF_SPANS[0]);
    accept(LOTS_OF_SPANS[0]);

    assertThat(sortTrace(traces().getTrace(LOTS_OF_SPANS[0].traceId()).execute()))
      .containsExactly(LOTS_OF_SPANS[0]);
  }
}
