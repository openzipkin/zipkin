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
package zipkin.storage.mysql;

import java.util.Iterator;
import org.jooq.Record;
import org.jooq.TableField;
import zipkin.internal.DependencyLinkSpan;
import zipkin.internal.Nullable;
import zipkin.internal.PeekingIterator;
import zipkin.storage.mysql.internal.generated.tables.ZipkinSpans;

import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

/**
 * Convenience that lazy converts rows into {@linkplain DependencyLinkSpan} objects.
 *
 * <p>Out-of-date schemas may be missing the trace_id_high field. When present, this becomes {@link
 * DependencyLinkSpan.TraceId#hi} used as the left-most 16 characters of the traceId in logging
 * statements.
 */
final class DependencyLinkSpanIterator implements Iterator<DependencyLinkSpan> {

  /** Assumes the input records are sorted by trace id, span id */
  static final class ByTraceId implements Iterator<Iterator<DependencyLinkSpan>> {
    final PeekingIterator<Record> delegate;
    final boolean hasTraceIdHigh;

    @Nullable Long currentTraceIdHi;
    long currentTraceIdLo;

    ByTraceId(Iterator<Record> delegate, boolean hasTraceIdHigh) {
      this.delegate = new PeekingIterator<>(delegate);
      this.hasTraceIdHigh = hasTraceIdHigh;
    }

    @Override public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override public Iterator<DependencyLinkSpan> next() {
      currentTraceIdHi = hasTraceIdHigh ? traceIdHigh(delegate) : null;
      currentTraceIdLo = delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
      return new DependencyLinkSpanIterator(delegate, currentTraceIdHi, currentTraceIdLo);
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  final PeekingIterator<Record> delegate;
  @Nullable final Long traceIdHi;
  final long traceIdLo;

  DependencyLinkSpanIterator(PeekingIterator<Record> delegate, Long traceIdHi, long traceIdLo) {
    this.delegate = delegate;
    this.traceIdHi = traceIdHi;
    this.traceIdLo = traceIdLo;
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext()
        // We don't have a query parameter for strictTraceId when fetching dependency links, so we
        // ignore traceIdHigh. Otherwise, a single trace can appear as two, doubling callCount.
        && delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID) == traceIdLo;
  }

  @Override
  public DependencyLinkSpan next() {
    Record row = delegate.peek();

    long spanId = row.getValue(ZipkinSpans.ZIPKIN_SPANS.ID);
    DependencyLinkSpan.Builder result = DependencyLinkSpan.builder(
        traceIdHi != null ? traceIdHi : 0L,
        traceIdLo,
        row.getValue(ZipkinSpans.ZIPKIN_SPANS.PARENT_ID),
        spanId
    );

    while (hasNext()) {
      Record next = delegate.peek();
      if (next == null) continue;

      if (spanId != next.getValue(ZipkinSpans.ZIPKIN_SPANS.ID)) break;
      delegate.next(); // advance the iterator since we are in the same span id

      String key = emptyToNull(next, ZIPKIN_ANNOTATIONS.A_KEY);
      String value = emptyToNull(next, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
      if (key == null || value == null) continue; // neither client nor server
      switch (key) {
        case CLIENT_ADDR:
          result.caService(value);
          break;
        case CLIENT_SEND:
          result.csService(value);
          break;
        case SERVER_ADDR:
          result.saService(value);
          break;
        case SERVER_RECV:
          result.srService(value);
      }
    }
    return result.build();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  static long traceIdHigh(PeekingIterator<Record> delegate) {
    return delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH);
  }

  static String emptyToNull(Record next, TableField<Record, String> field) {
    String result = next.getValue(field);
    return result != null && !"".equals(result) ? result : null;
  }
}
