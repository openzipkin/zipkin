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
package zipkin.storage;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.Span;
import zipkin.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class ITInMemoryStorage {

  public static class DependenciesTest extends zipkin.storage.DependenciesTest {
    final InMemoryStorage storage = InMemoryStorage.builder().build();

    @Override protected InMemoryStorage storage() {
      return storage;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  public static class SpanStoreTest extends zipkin.storage.SpanStoreTest {
    final InMemoryStorage storage = InMemoryStorage.builder().build();

    @Override protected InMemoryStorage storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }

    /** This shows when spans are sent multiple times. Doing so can reveal instrumentation bugs. */
    @Test public void getRawTrace_sameSpanTwice() {
      Span span = TestObjects.LOTS_OF_SPANS[0];
      accept(span);
      accept(span);

      assertThat(store().getRawTrace(span.traceIdHigh, span.traceId))
        .containsExactly(span, span);
    }
  }

  public static class StrictTraceIdFalseTest extends zipkin.storage.StrictTraceIdFalseTest {
    final InMemoryStorage storage = InMemoryStorage.builder().strictTraceId(false).build();

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }
}
