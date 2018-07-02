/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.storage.ITSpanStore.sortTraces;

/**
 * Base test for when {@link StorageComponent.Builder#strictTraceId(boolean) strictTraceId ==
 * false}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITStrictTraceIdFalse {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected SpanStore store() {
    return storage().spanStore();
  }

  /** Clears store between tests. */
  @Before public abstract void clear() throws Exception;

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
  @Test
  public void getTraces_retrievesBy64Or128BitTraceId() throws Exception {
    accept(with128BitId1, with64BitId1, with128BitId2, with64BitId2, with128BitId3, with64BitId3);

    List<List<Span>> trace1And3 = asList(
      asList(with128BitId1, with64BitId1),
      asList(with128BitId3, with64BitId3)
    );

    List<List<Span>> resultsWithBothIdLength = sortTraces(store()
      .getTraces(asList(
        with128BitId1.traceId(),
        with64BitId1.traceId(),
        with128BitId3.traceId(),
        with64BitId3.traceId()
      )).execute());

    assertThat(resultsWithBothIdLength).containsExactlyElementsOf(trace1And3);

    List<List<Span>> resultsWith64BitIdLength = sortTraces(store()
      .getTraces(asList(
        with64BitId1.traceId(), with64BitId3.traceId()
      )).execute());

    assertThat(resultsWith64BitIdLength).containsExactlyElementsOf(trace1And3);

    List<List<Span>> resultsWith128BitIdLength = sortTraces(store()
      .getTraces(asList(
        with128BitId1.traceId(), with128BitId3.traceId()
      )).execute());

    assertThat(resultsWith128BitIdLength).containsExactlyElementsOf(trace1And3);
  }

  protected void accept(Span... spans) throws IOException {
    storage().spanConsumer().accept(asList(spans)).execute();
  }
}
