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
import zipkin.BinaryAnnotation.Type;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.internal.Nullable;
import zipkin.internal.PeekingIterator;
import zipkin.internal.Span2;
import zipkin.storage.mysql.internal.generated.tables.ZipkinSpans;

import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.ERROR;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.internal.Util.equal;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

/**
 * Lazy converts rows into {@linkplain Span2} objects suitable for dependency links. This takes
 * short-cuts to require less data. For example, it folds shared RPC spans into one, and doesn't
 * include tags, non-core annotations or time units.
 *
 * <p>Out-of-date schemas may be missing the trace_id_high field. When present, this becomes {@link
 * Span2#traceIdHigh()} used as the left-most 16 characters of the traceId in logging
 * statements.
 */
final class DependencyLinkSpan2Iterator implements Iterator<Span2> {

  /** Assumes the input records are sorted by trace id, span id */
  static final class ByTraceId implements Iterator<Iterator<Span2>> {
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

    @Override public Iterator<Span2> next() {
      currentTraceIdHi = hasTraceIdHigh ? traceIdHigh(delegate) : null;
      currentTraceIdLo = delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
      return new DependencyLinkSpan2Iterator(delegate, currentTraceIdHi, currentTraceIdLo);
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  final PeekingIterator<Record> delegate;
  @Nullable final Long traceIdHi;
  final long traceIdLo;

  DependencyLinkSpan2Iterator(PeekingIterator<Record> delegate, Long traceIdHi, long traceIdLo) {
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
  public Span2 next() {
    Record row = delegate.peek();

    long spanId = row.getValue(ZipkinSpans.ZIPKIN_SPANS.ID);
    boolean error = false;
    String srService = null, csService = null, caService = null, saService = null;
    while (hasNext()) { // there are more values for this trace
      if (spanId != delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.ID)) {
        break; // if we are in a new span
      }
      Record next = delegate.next(); // row for the same span

      String key = emptyToNull(next, ZIPKIN_ANNOTATIONS.A_KEY);
      String value = emptyToNull(next, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
      if (key == null || value == null) continue; // neither client nor server
      switch (key) {
        case CLIENT_ADDR:
          caService = value;
          break;
        case CLIENT_SEND:
          csService = value;
          break;
        case SERVER_ADDR:
          saService = value;
          break;
        case SERVER_RECV:
          srService = value;
          break;
        case ERROR:
          // a span is in error if it has a tag, not an annotation, of name "error"
          error = Type.STRING.value == next.get(ZIPKIN_ANNOTATIONS.A_TYPE);
      }
    }

    // The client address is more authoritative than the client send owner.
    if (caService == null) caService = csService;

    // Finagle labels two sides of the same socket ("ca", "sa") with the same name.
    // Skip the client side, so it isn't mistaken for a loopback request
    if (equal(saService, caService)) caService = null;

    Span2.Builder result = Span2.builder()
      .traceIdHigh(traceIdHi != null ? traceIdHi : 0L)
      .traceId(traceIdLo)
      .parentId(row.getValue(ZipkinSpans.ZIPKIN_SPANS.PARENT_ID))
      .id(spanId);

    if (error) {
      result.putTag(Constants.ERROR, "" /* actual value doesn't matter */);
    }

    if (srService != null) {
      return result.kind(Span2.Kind.SERVER)
        .localEndpoint(ep(srService))
        .remoteEndpoint(ep(caService))
        .build();
    } else if (saService != null) {
      return result
        .kind(csService != null ? Span2.Kind.CLIENT : null)
        .localEndpoint(ep(caService))
        .remoteEndpoint(ep(saService))
        .build();
    } else if (csService != null) {
      return result.kind(Span2.Kind.SERVER)
        .localEndpoint(ep(caService))
        .build();
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

  static @Nullable String emptyToNull(Record next, TableField<Record, String> field) {
    String result = next.getValue(field);
    return result != null && !"".equals(result) ? result : null;
  }

  static Endpoint ep(@Nullable String serviceName) {
    return serviceName != null ? Endpoint.builder().serviceName(serviceName).build() : null;
  }
}
