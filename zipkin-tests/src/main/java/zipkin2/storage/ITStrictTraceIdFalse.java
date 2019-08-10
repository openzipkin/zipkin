/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.ITSpanStore.requestBuilder;
import static zipkin2.storage.ITSpanStore.sortTrace;
import static zipkin2.storage.ITSpanStore.sortTraces;

/**
 * Base test for when {@link StorageComponent.Builder#strictTraceId(boolean) strictTraceId ==
 * false}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 *
 * <p>This is a replacement for {@code zipkin.storage.StrictTraceIdFalseTest}.
 */
public abstract class ITStrictTraceIdFalse<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    storage.strictTraceId(false);
  }

  /** Ensures we can still lookup fully 128-bit traces when strict trace ID id disabled */
  @Test void getTraces_128BitTraceId() throws IOException {
    getTraces_128BitTraceId(accept128BitTrace(storage()));
  }

  @Test void getTraces_128BitTraceId_mixed() throws IOException {
    getTraces_128BitTraceId(acceptMixedTrace());
  }

  protected void getTraces_128BitTraceId(List<Span> trace) throws IOException {
    assertThat(sortTraces(store().getTraces(requestBuilder().build()).execute()))
      .containsExactly(trace);

    // search by 128-bit side's service and data
    assertThat(sortTraces(store().getTraces(requestBuilder()
      .serviceName(TestObjects.FRONTEND.serviceName())
      .parseAnnotationQuery("foo").build()).execute()))
      .containsExactly(trace);

    // search by 64-bit side's service and data
    assertThat(sortTraces(store().getTraces(requestBuilder()
      .serviceName(TestObjects.BACKEND.serviceName())
      .parseAnnotationQuery("error").build()).execute()))
      .containsExactly(trace);
  }

  @Test void getTrace_retrievesBy64Or128BitTraceId() throws IOException {
    List<Span> trace = accept128BitTrace(storage());

    retrievesBy64Or128BitTraceId(trace);
  }

  @Test void getTrace_retrievesBy64Or128BitTraceId_mixed() throws IOException {
    List<Span> trace = acceptMixedTrace();

    retrievesBy64Or128BitTraceId(trace);
  }

  void retrievesBy64Or128BitTraceId(List<Span> trace) throws IOException {
    assertThat(store().getTrace(trace.get(0).traceId().substring(16)).execute())
      .containsOnlyElementsOf(trace);
    assertThat(store().getTrace(trace.get(0).traceId()).execute())
      .containsOnlyElementsOf(trace);
  }

  protected List<Span> accept128BitTrace(StorageComponent storage) throws IOException {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    Collections.reverse(trace);
    storage.spanConsumer().accept(trace).execute();
    return TestObjects.TRACE;
  }

  List<Span> acceptMixedTrace() throws IOException {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    String downgraded = trace.get(0).traceId().substring(16);
    // iterate after the outbound client span, emulating a server that downgraded
    for (int i = 2; i < trace.size(); i++) {
      trace.set(i, trace.get(i).toBuilder().traceId(downgraded).build());
    }
    Collections.reverse(trace);
    accept(trace.toArray(new Span[0]));
    return sortTrace(trace);
  }

  void accept(Span... spans) throws IOException {
    spanConsumer().accept(asList(spans)).execute();
  }
}

