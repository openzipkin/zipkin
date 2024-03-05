/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.TestObjects.requestBuilder;

class StrictTraceIdTest {

  @Test void filterTraces_skipsOnNoClash() {
    Span oneOne = Span.newBuilder().traceId(1, 1).id(1).build();
    Span oneTwo = Span.newBuilder().traceId(1, 2).id(1).build();
    List<List<Span>> traces = List.of(List.of(oneOne), List.of(oneTwo));

    assertThat(StrictTraceId.filterTraces(
      requestBuilder().spanName("11").build()
    ).map(traces)).isSameAs(traces);
  }

  @Test void filterTraces_onSpanName() {
    assertThat(StrictTraceId.filterTraces(
      requestBuilder().spanName("11").build()
    ).map(traces())).flatExtracting(l -> l).isEmpty();

    assertThat(StrictTraceId.filterTraces(
      requestBuilder().spanName("1").build()
    ).map(traces())).containsExactly(traces().get(0));
  }

  @Test void filterTraces_onTag() {
    assertThat(StrictTraceId.filterTraces(
      requestBuilder().parseAnnotationQuery("foo=0").build()
    ).map(traces())).flatExtracting(l -> l).isEmpty();

    assertThat(StrictTraceId.filterTraces(
      requestBuilder().parseAnnotationQuery("foo=1").build()
    ).map(traces())).containsExactly(traces().get(0));
  }

  @Test void filterSpans() {
    ArrayList<Span> trace = new ArrayList<>(TRACE);

    assertThat(StrictTraceId.filterSpans(CLIENT_SPAN.traceId()).map(trace))
      .isEqualTo(TRACE);

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

    return new ArrayList<>(List.of(List.of(span1), List.of(span2), List.of(span3)));
  }

  @Test void hasClashOnLowerTraceId() {
    Span oneOne = Span.newBuilder().traceId(1, 1).id(1).build();
    Span twoOne = Span.newBuilder().traceId(2, 1).id(1).build();
    Span zeroOne = Span.newBuilder().traceId(0, 1).id(1).build();
    Span oneTwo = Span.newBuilder().traceId(1, 2).id(1).build();

    assertThat(StrictTraceId.hasClashOnLowerTraceId(List.of(List.of(oneOne), List.of(oneTwo))))
      .isFalse();
    assertThat(StrictTraceId.hasClashOnLowerTraceId(List.of(List.of(oneOne), List.of(twoOne))))
      .isTrue();
    assertThat(StrictTraceId.hasClashOnLowerTraceId(List.of(List.of(oneOne), List.of(zeroOne))))
      .isTrue();
  }
}
