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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.storage.ITSpanStore.requestBuilder;

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
  @Test protected void getTraces_128BitTraceId() throws IOException {
    getTraces_128BitTraceId(accept128BitTrace(storage));
  }

  @Test protected void getTraces_128BitTraceId_mixed() throws IOException {
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

  @Test protected void getTrace_retrievesBy64Or128BitTraceId() throws IOException {
    List<Span> trace = accept128BitTrace(storage);

    retrievesBy64Or128BitTraceId(trace);
  }

  @Test protected void getTrace_retrievesBy64Or128BitTraceId_mixed() throws IOException {
    List<Span> trace = acceptMixedTrace();

    retrievesBy64Or128BitTraceId(trace);
  }

  void retrievesBy64Or128BitTraceId(List<Span> trace) throws IOException {
    // spans are in reporting order (reverse topological)
    String traceId = trace.get(trace.size() - 1).traceId();

    assertThat(traces().getTrace(traceId.substring(16)).execute())
      .containsExactlyInAnyOrderElementsOf(trace);
    assertThat(traces().getTrace(traceId).execute())
      .containsExactlyInAnyOrderElementsOf(trace);
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

  Span with128BitId1 = Span.newBuilder()
    .traceId("baaaaaaaaaaaaaaaa").id("a").timestamp(TODAY * 1000).build();
  Span with64BitId1 = Span.newBuilder()
    .traceId("aaaaaaaaaaaaaaaa").id("b").timestamp((TODAY + 1) * 1000).build();
  Span with128BitId2 = Span.newBuilder()
    .traceId("21111111111111111").id("1").timestamp(TODAY * 1000).build();
  Span with64BitId2 = Span.newBuilder()
    .traceId("1111111111111111").id("2").timestamp((TODAY + 1) * 1000).build();
  Span with128BitId3 = Span.newBuilder()
    .traceId("effffffffffffffff").id("1").timestamp(TODAY * 1000).build();
  Span with64BitId3 = Span.newBuilder()
    .traceId("ffffffffffffffff").id("2").timestamp(TODAY * 1000).build();

  /** current implementation cannot return exact form reported */
  @Test protected void getTraces_retrievesBy64Or128BitTraceId() throws Exception {
    accept(with128BitId1, with64BitId1, with128BitId2, with64BitId2, with128BitId3, with64BitId3);

    List<List<Span>> trace1And3 = asList(
      asList(with128BitId1, with64BitId1),
      asList(with128BitId3, with64BitId3)
    );

    List<List<Span>> resultsWithBothIdLength = sortTraces(traces()
      .getTraces(asList(
        with128BitId1.traceId(),
        with64BitId1.traceId(),
        with128BitId3.traceId(),
        with64BitId3.traceId()
      )).execute());

    assertThat(resultsWithBothIdLength).containsExactlyElementsOf(trace1And3);

    List<List<Span>> resultsWith64BitIdLength = sortTraces(traces()
      .getTraces(asList(
        with64BitId1.traceId(), with64BitId3.traceId()
      )).execute());

    assertThat(resultsWith64BitIdLength).containsExactlyElementsOf(trace1And3);

    List<List<Span>> resultsWith128BitIdLength = sortTraces(traces()
      .getTraces(asList(
        with128BitId1.traceId(), with128BitId3.traceId()
      )).execute());

    assertThat(resultsWith128BitIdLength).containsExactlyElementsOf(trace1And3);
  }
}
