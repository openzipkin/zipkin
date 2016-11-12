/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test for when {@link StorageComponent.Builder#strictTraceId(boolean) strictTraceId
 * == true}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class StrictTraceIdFalseTest {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected SpanStore store() {
    return storage().spanStore();
  }

  /** Blocks until the callback completes to allow read-your-writes consistency during tests. */
  protected void accept(Span... spans) {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    storage().asyncSpanConsumer().accept(asList(spans), captor);
    captor.get(); // block on result
  }

  /** Clears store between tests. */
  @Before
  public abstract void clear() throws IOException;

  @Test
  public void getTraces_128BitTraceId_mixed() {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    trace.set(0, trace.get(0).toBuilder().traceIdHigh(1).build());
    // pretend the others downgraded to 64-bit trace IDs

    accept(trace.toArray(new Span[0]));

    assertThat(store().getTraces(QueryRequest.builder().build()))
        .containsExactly(trace);
  }

  @Test
  public void getTrace_retrieves128bitTraceIdByLower64Bits_mixed() {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    trace.set(0, trace.get(0).toBuilder().traceIdHigh(1).build());
    // pretend the others downgraded to 64-bit trace IDs

    accept(trace.toArray(new Span[0]));

    assertThat(store().getTrace(0L, trace.get(0).traceId))
        .containsExactlyElementsOf(trace);
    assertThat(store().getTrace(trace.get(0).traceIdHigh, trace.get(0).traceId))
        .containsExactlyElementsOf(trace);
  }
}
