/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.internal.DependencyLinker;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class AggregateDependencies implements Function<DSLContext, List<DependencyLink>> {
  final Schema schema;
  final long startTsBegin, startTsEnd;

  AggregateDependencies(Schema schema, long startTsBegin, long startTsEnd) {
    this.schema = schema;
    this.startTsBegin = startTsBegin;
    this.startTsEnd = startTsEnd;
  }

  @Override
  public List<DependencyLink> apply(DSLContext context) {
    // Subquery on trace IDs to prevent only matching the part of the trace that exists within
    // the interval: we want all of the trace.
    SelectConditionStep<Record1<Long>> traceIDs = context.selectDistinct(ZIPKIN_SPANS.TRACE_ID)
      .from(ZIPKIN_SPANS)
      .where(startTsBegin == startTsEnd
        ? ZIPKIN_SPANS.START_TS.lessOrEqual(startTsEnd)
        : ZIPKIN_SPANS.START_TS.between(startTsBegin, startTsEnd));
    // Lazy fetching the cursor prevents us from buffering the whole dataset in memory.
    Cursor<Record> cursor = context.selectDistinct(schema.dependencyLinkerFields)
      // left joining allows us to keep a mapping of all span ids, not just ones that have
      // special annotations. We need all span ids to reconstruct the trace tree. We need
      // the whole trace tree so that we can accurately skip local spans.
      .from(ZIPKIN_SPANS.leftJoin(ZIPKIN_ANNOTATIONS)
        // NOTE: we are intentionally grouping only on the low-bits of trace id. This
        // buys time for applications to upgrade to 128-bit instrumentation.
        .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID)
          .and(ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID)))
        .and(ZIPKIN_ANNOTATIONS.A_KEY.in("lc", "cs", "ca", "sr", "sa", "ma", "mr", "ms", "error")))
      .where(ZIPKIN_SPANS.TRACE_ID.in(traceIDs))
      // Grouping so that later code knows when a span or trace is finished.
      .groupBy(schema.dependencyLinkerGroupByFields)
      .fetchLazy();

    Iterator<Iterator<Span>> traces =
      new DependencyLinkV2SpanIterator.ByTraceId(cursor.iterator(), schema.hasTraceIdHigh);

    if (!traces.hasNext()) return List.of();

    DependencyLinker linker = new DependencyLinker();

    List<Span> nextTrace = new ArrayList<>();
    while (traces.hasNext()) {
      Iterator<Span> i = traces.next();
      while (i.hasNext()) nextTrace.add(i.next());
      linker.putTrace(nextTrace);
      nextTrace.clear();
    }

    return linker.link();
  }

  @Override
  public String toString() {
    return "AggregateDependencies{"
      + "startTsBegin="
      + startTsBegin
      + ", startTsEnd="
      + startTsEnd
      + '}';
  }
}
