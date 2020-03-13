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
package zipkin2.internal;

import org.junit.jupiter.api.Test;
import zipkin2.TestObjects;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class TracesAdapterTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  TracesAdapter adapter = new TracesAdapter(storage);

  /**
   * The contract for {@link zipkin2.storage.SpanStore#getTrace(java.lang.String)} is to return
   * empty on not found. This ensures a list of results aren't padded with empty ones.
   */
  @Test void getTraces_doesntReturnEmptyElements() throws Exception {
    storage.accept(TestObjects.TRACE).execute();

    assertThat(adapter.getTraces(asList()).execute())
      .isEmpty();

    assertThat(adapter.getTraces(asList("1")).execute())
      .isEmpty();

    assertThat(adapter.getTraces(asList("1", "2")).execute())
      .isEmpty();

    assertThat(adapter.getTraces(asList("1", TestObjects.TRACE.get(0).traceId(), "3")).execute())
      .containsExactly(TestObjects.TRACE);
  }
}
