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
package zipkin2.storage.mysql.v1;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jooq.Record;
import org.jooq.TableField;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans;
import zipkin2.v1.V1BinaryAnnotation;

import static zipkin2.storage.mysql.v1.Schema.maybeGet;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

/**
 * Lazy converts rows into {@linkplain Span} objects suitable for dependency links. This takes
 * short-cuts to require less data. For example, it folds shared RPC spans into one, and doesn't
 * include tags, non-core annotations or time units.
 *
 * <p>Out-of-date schemas may be missing the trace_id_high field. When present, the {@link
 * Span#traceId()} could be 32 characters in logging statements.
 */
final class DependencyLinkV2SpanIterator implements Iterator<Span> {

  /** Assumes the input records are sorted by trace id, span id */
  static final class ByTraceId implements Iterator<Iterator<Span>> {
    final PeekingIterator<Record> delegate;
    final boolean hasTraceIdHigh;

    long currentTraceIdHi, currentTraceIdLo;

    ByTraceId(Iterator<Record> delegate, boolean hasTraceIdHigh) {
      this.delegate = new PeekingIterator<>(delegate);
      this.hasTraceIdHigh = hasTraceIdHigh;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Iterator<Span> next() {
      if (!hasNext()) throw new NoSuchElementException();
      currentTraceIdHi = hasTraceIdHigh ? traceIdHigh(delegate) : 0L;
      currentTraceIdLo = delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
      return new DependencyLinkV2SpanIterator(delegate, currentTraceIdHi, currentTraceIdLo);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  final PeekingIterator<Record> delegate;
  final long traceIdHi, traceIdLo;

  DependencyLinkV2SpanIterator(PeekingIterator<Record> delegate, long traceIdHi, long traceIdLo) {
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
  public Span next() {
    if (!hasNext()) throw new NoSuchElementException();
    Record row = delegate.peek();

    long spanId = row.getValue(ZipkinSpans.ZIPKIN_SPANS.ID);
    boolean error = false;
    String lcService = null, srService = null, csService = null, caService = null, saService = null,
      maService = null, mrService = null, msService = null;
    while (hasNext()) { // there are more values for this trace
      if (spanId != delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.ID)) {
        break; // if we are in a new span
      }
      Record next = delegate.next(); // row for the same span

      String key = emptyToNull(next, ZIPKIN_ANNOTATIONS.A_KEY);
      String value = emptyToNull(next, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
      if (key == null || value == null) continue; // neither client nor server
      switch (key) {
        case "lc":
          lcService = value;
          break;
        case "ca":
          caService = value;
          break;
        case "cs":
          csService = value;
          break;
        case "ma":
          maService = value;
          break;
        case "mr":
          mrService = value;
          break;
        case "ms":
          msService = value;
          break;
        case "sa":
          saService = value;
          break;
        case "sr":
          srService = value;
          break;
        case "error":
          // a span is in error if it has a tag, not an annotation, of name "error"
          error = V1BinaryAnnotation.TYPE_STRING == next.get(ZIPKIN_ANNOTATIONS.A_TYPE);
      }
    }

    // The client address is more authoritative than the client send owner.
    if (caService == null) caService = csService;

    // Finagle labels two sides of the same socket ("ca", "sa") with the same name.
    // Skip the client side, so it isn't mistaken for a loopback request
    if (saService != null && saService.equals(caService)) caService = null;

    long parentId = maybeGet(row, ZipkinSpans.ZIPKIN_SPANS.PARENT_ID, 0L);
    Span.Builder result =
        Span.newBuilder().traceId(traceIdHi, traceIdLo).parentId(parentId).id(spanId);

    if (error) {
      result.putTag("error", "" /* actual value doesn't matter */);
    }

    if (srService != null) {
      return result
          .kind(Span.Kind.SERVER)
          .localEndpoint(ep(srService))
          .remoteEndpoint(ep(caService))
          .build();
    } else if (saService != null) {
      Endpoint localEndpoint = ep(caService);
      // When span.kind is missing, the local endpoint is "lc" and the remote endpoint is "sa"
      if (localEndpoint == null) localEndpoint = ep(lcService);
      return result
          .kind(csService != null ? Span.Kind.CLIENT : null)
          .localEndpoint(localEndpoint)
          .remoteEndpoint(ep(saService))
          .build();
    } else if (csService != null) {
      return result.kind(Span.Kind.SERVER).localEndpoint(ep(caService)).build();
    } else if (mrService != null) {
      return result
        .kind(Span.Kind.CONSUMER)
        .localEndpoint(ep(mrService))
        .remoteEndpoint(ep(maService))
        .build();
    } else if (msService != null) {
      return result
        .kind(Span.Kind.PRODUCER)
        .localEndpoint(ep(msService))
        .remoteEndpoint(ep(maService))
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
    return serviceName != null ? Endpoint.newBuilder().serviceName(serviceName).build() : null;
  }
}
