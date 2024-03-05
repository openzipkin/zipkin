/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TraceTest {

  /**
   * Some don't propagate the server's parent ID which creates a race condition. Try to unwind it.
   *
   * <p>See https://github.com/openzipkin/zipkin/pull/1745
   */
  @Test void backfillsMissingParentIdOnSharedSpan() {
    List<Span> trace = List.of(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false),
      // below the parent ID is null as it wasn't propagated
      span("a", null, "b", Kind.SERVER, "backend", null, true)
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false),
      span("a", "a", "b", Kind.SERVER, "backend", null, true)
    );
  }

  @Test void backfillsMissingSharedFlag() {
    List<Span> trace = List.of(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", "1.2.3.4", false),
      // below the shared flag was forgotten
      span("a", "a", "b", Kind.SERVER, "backend", "5.6.7.8", false)
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", "1.2.3.4", false),
      span("a", "a", "b", Kind.SERVER, "backend", "5.6.7.8", true)
    );
  }

  /** Some truncate an incoming trace ID to 64-bits. */
  @Test void choosesBestTraceId() {
    List<Span> trace = List.of(
      span("7180c278b62e8f6a216a2aea45d08fc9", null, "a", Kind.SERVER, "frontend", null, false),
      span("7180c278b62e8f6a216a2aea45d08fc9", "a", "b", Kind.CLIENT, "frontend", null, false),
      span("216a2aea45d08fc9", "a", "b", Kind.SERVER, "backend", null, true)
    );

    assertThat(Trace.merge(trace)).flatExtracting(Span::traceId).containsExactly(
      "7180c278b62e8f6a216a2aea45d08fc9",
      "7180c278b62e8f6a216a2aea45d08fc9",
      "7180c278b62e8f6a216a2aea45d08fc9"
    );
  }

  /** Let's pretend people use crappy data, but only on the first hop. */
  @Test void mergesWhenMissingEndpoints() {
    List<Span> trace = List.of(
      Span.newBuilder()
        .traceId("a")
        .id("a")
        .putTag("service", "frontend")
        .putTag("span.kind", "SERVER")
        .build(),
      Span.newBuilder()
        .traceId("a")
        .parentId("a")
        .id("b")
        .putTag("service", "frontend")
        .putTag("span.kind", "CLIENT")
        .timestamp(1L)
        .build(),
      span("a", "a", "b", Kind.SERVER, "backend", null, true),
      Span.newBuilder().traceId("a").parentId("a").id("b").duration(10L).build()
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      Span.newBuilder()
        .traceId("a")
        .id("a")
        .putTag("service", "frontend")
        .putTag("span.kind", "SERVER")
        .build(),
      Span.newBuilder()
        .traceId("a")
        .parentId("a")
        .id("b")
        .putTag("service", "frontend")
        .putTag("span.kind", "CLIENT")
        .timestamp(1L)
        .duration(10L)
        .build(),
      span("a", "a", "b", Kind.SERVER, "backend", null, true)
    );
  }

  /**
   * If a client request is proxied by something that does transparent retried. It can be the case
   * that two servers share the same ID (accidentally!)
   */
  @Test void doesntMergeSharedSpansOnDifferentIPs() {
    List<Span> trace = List.of(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false).toBuilder()
        .timestamp(1L).addAnnotation(3L, "brave.flush").build(),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.4", true),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", true),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false).toBuilder()
        .duration(10L).build()
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false).toBuilder()
        .timestamp(1L).duration(10L).addAnnotation(3L, "brave.flush").build(),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.4", true),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", true)
    );
  }

  // Same as above, but the late reported data has no parent id or endpoint
  @Test void putsRandomDataOnFirstSpanWithEndpoint() {
    List<Span> trace = List.of(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, null, null, false),
      span("a", "a", "b", null, "frontend", null, false).toBuilder()
        .timestamp(1L).addAnnotation(3L, "brave.flush").build(),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.4", true),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", true),
      span("a", "a", "b", null, null, null, false).toBuilder()
        .duration(10L).build()
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false).toBuilder()
        .timestamp(1L).duration(10L).addAnnotation(3L, "brave.flush").build(),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.4", true),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", true)
    );
  }

  // not a good idea to send parts of a local endpoint separately, but this helps ensure data isn't
  // accidentally partitioned in a overly fine grain
  @Test void mergesIncompleteEndpoints() {
    List<Span> trace = List.of(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, null, "1.2.3.4", false),
      span("a", "a", "b", Kind.SERVER, null, "1.2.3.5", true),
      span("a", "a", "b", Kind.SERVER, "backend", null, true)
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", "1.2.3.4", false),
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", true)
    );
  }

  @Test void deletesSelfReferencingParentId() {
    List<Span> trace = List.of(
      span("a", "a", "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false)
    );

    assertThat(Trace.merge(trace)).usingFieldByFieldElementComparator().containsExactlyInAnyOrder(
      span("a", null, "a", Kind.SERVER, "frontend", null, false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false)
    );
  }

  @Test void worksWhenMissingParentSpan() {
    String missingParentId = "a";
    List<Span> trace = List.of(
      span("a", missingParentId, "b", Kind.SERVER, "backend", "1.2.3.4", false),
      span("a", missingParentId, "c", Kind.SERVER, "backend", null, false)
    );

    assertThat(Trace.merge(trace)).containsExactlyElementsOf(trace);
  }

  // some instrumentation don't add shared flag to servers
  @Test void cleanupComparator_ordersClientFirst() {
    List<Span> trace = Arrays.asList( // to allow sorting
      span("a", "a", "b", Kind.SERVER, "backend", "1.2.3.5", false),
      span("a", "a", "b", Kind.CLIENT, "frontend", null, false)
    );

    Collections.sort(trace, Trace.CLEANUP_COMPARATOR);
    assertThat(trace.get(0).kind()).isEqualTo(Kind.CLIENT);
  }

  /** Comparators are meant to be transitive. This exploits edge cases to fool our comparator. */
  @Test void cleanupComparator_transitiveKindComparison() {
    List<Span> trace = new ArrayList<>();
    Endpoint aEndpoint = Endpoint.newBuilder().serviceName("a").build();
    Endpoint bEndpoint = Endpoint.newBuilder().serviceName("b").build();
    Span template = Span.newBuilder().traceId("a").id("a").build();
    // If there is a transitive ordering problem, TimSort will throw an IllegalArgumentException
    // when there are at least 32 elements.
    for (int i = 0, length = 7; i < length; i++) {
      trace.add(template.toBuilder().shared(true).localEndpoint(bEndpoint).build());
      trace.add(template.toBuilder().kind(Kind.CLIENT).localEndpoint(bEndpoint).build());
      trace.add(template.toBuilder().localEndpoint(aEndpoint).build());
      trace.add(template);
      trace.add(template.toBuilder().kind(Kind.CLIENT).localEndpoint(aEndpoint).build());
    }

    Collections.sort(trace, Trace.CLEANUP_COMPARATOR);

    assertThat(new LinkedHashSet<>(trace))
      .extracting(Span::shared, Span::kind, s -> s.localServiceName())
      .containsExactly(
        tuple(null, Kind.CLIENT, "a"),
        tuple(null, Kind.CLIENT, "b"),
        tuple(null, null, null),
        tuple(null, null, "a"),
        tuple(true, null, "b")
      );
  }

  static Span span(String traceId, @Nullable String parentId, String id, @Nullable Kind kind,
    @Nullable String local, @Nullable String ip, boolean shared) {
    Span.Builder result = Span.newBuilder().traceId(traceId).parentId(parentId).id(id).kind(kind);
    if (local != null || ip != null) {
      result.localEndpoint(Endpoint.newBuilder().serviceName(local).ip(ip).build());
    }
    return result.shared(shared).build();
  }
}
