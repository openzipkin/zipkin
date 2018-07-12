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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.storage.ITSpanStore.requestBuilder;

public class StrictTraceIdTest {

  @Test public void filterTraces_onSpanName() {
    assertThat(StrictTraceId.filterTraces(
      requestBuilder().spanName("11").build()
    ).map(traces())).flatExtracting(l -> l).isEmpty();

    assertThat(StrictTraceId.filterTraces(
      requestBuilder().spanName("1").build()
    ).map(traces())).containsExactly(traces().get(0));
  }

  @Test public void filterTraces_onTag() {
    assertThat(StrictTraceId.filterTraces(
      requestBuilder().parseAnnotationQuery("foo=0").build()
    ).map(traces())).flatExtracting(l -> l).isEmpty();

    assertThat(StrictTraceId.filterTraces(
      requestBuilder().parseAnnotationQuery("foo=1").build()
    ).map(traces())).containsExactly(traces().get(0));
  }

  @Test public void filterSpans() {
    assertThat(StrictTraceId.filterSpans(CLIENT_SPAN.traceId()).map(TRACE)).isEqualTo(TRACE);

    ArrayList<Span> trace = new ArrayList<>(TRACE);
    trace.set(1, CLIENT_SPAN.toBuilder().traceId(CLIENT_SPAN.traceId().substring(16)).build());
    assertThat(StrictTraceId.filterSpans(CLIENT_SPAN.traceId()).map(trace))
      .doesNotContain(CLIENT_SPAN);
  }

  List<List<Span>> traces() {
    // 64-bit trace ID
    Span span1 = Span.newBuilder().traceId(CLIENT_SPAN.traceId().substring(16)).id("1")
      .name("1")
      .putTag("foo", "1")
      .timestamp(TODAY * 1000L)
      .localEndpoint(FRONTEND)
      .build();
    // 128-bit trace ID prefixed by above
    Span span2 =
      span1.toBuilder().traceId(CLIENT_SPAN.traceId()).name("2").putTag("foo", "2").build();
    // Different 128-bit trace ID prefixed by above
    Span span3 =
      span1.toBuilder().traceId("1" + span1.traceId()).name("3").putTag("foo", "3").build();

    return new ArrayList<>(asList(asList(span1), asList(span2), asList(span3)));
  }
}
