/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
import org.jooq.Record5;
import zipkin.internal.DependencyLinkSpan;
import zipkin.internal.PeekingIterator;
import zipkin.storage.mysql.internal.generated.tables.ZipkinSpans;

import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

/** Convenience that lazy converts rows into {@linkplain DependencyLinkSpan} objects. */
final class DependencyLinkSpanIterator implements Iterator<DependencyLinkSpan> {

  /** Assumes the input records are sorted by trace id, span id */
  static final class ByTraceId implements Iterator<Iterator<DependencyLinkSpan>> {
    final PeekingIterator<Record5<Long, Long, Long, String, String>> delegate;

    Long currentTraceId;

    ByTraceId(Iterator<Record5<Long, Long, Long, String, String>> delegate) {
      this.delegate = new PeekingIterator<>(delegate);
    }

    @Override public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override public Iterator<DependencyLinkSpan> next() {
      currentTraceId = delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
      return new DependencyLinkSpanIterator(delegate, currentTraceId);
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  final PeekingIterator<Record5<Long, Long, Long, String, String>> delegate;
  final Long traceId;

  DependencyLinkSpanIterator(PeekingIterator<Record5<Long, Long, Long, String, String>> delegate,
      Long traceId) {
    this.delegate = delegate;
    this.traceId = traceId;
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext() && delegate.peek().getValue(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID).equals(traceId);
  }

  @Override
  public DependencyLinkSpan next() {
    Record5<Long, Long, Long, String, String> row = delegate.next();

    DependencyLinkSpan.Builder result = DependencyLinkSpan.builder(
        traceId,
        row.getValue(ZipkinSpans.ZIPKIN_SPANS.PARENT_ID),
        row.getValue(ZipkinSpans.ZIPKIN_SPANS.ID)
    );
    parseClientAndServerNames(
        result,
        row.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
        row.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME));

    while (hasNext()) {
      Record5<Long, Long, Long, String, String> next = delegate.peek();
      if (next == null) {
        continue;
      }
      if (row.getValue(ZipkinSpans.ZIPKIN_SPANS.ID).equals(next.getValue(ZipkinSpans.ZIPKIN_SPANS.ID))) {
        delegate.next(); // advance the iterator since we are in the same span id
        parseClientAndServerNames(
            result,
            next.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
            next.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME));
      } else {
        break;
      }
    }
    return result.build();
  }

  void parseClientAndServerNames(DependencyLinkSpan.Builder span, String key, String value) {
    if (key == null) return; // neither client nor server
    switch (key) {
      case CLIENT_ADDR:
        span.caService(value);
        break;
      case CLIENT_SEND:
        span.csService(value);
        break;
      case SERVER_ADDR:
        span.saService(value);
        break;
      case SERVER_RECV:
        span.srService(value);
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
