/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import zipkin2.Span;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

/** This type isn't thread-safe: it re-uses state to avoid re-allocations in conversion loops. */
// @Immutable
public final class V1JsonSpanWriter implements WriteBuffer.Writer<Span> {
  final V2SpanConverter converter = V2SpanConverter.create();
  final V1SpanWriter v1SpanWriter = new V1SpanWriter();

  @Override public int sizeInBytes(Span value) {
    V1Span v1Span = converter.convert(value);
    return v1SpanWriter.sizeInBytes(v1Span);
  }

  @Override public void write(Span value, WriteBuffer b) {
    V1Span v1Span = converter.convert(value);
    v1SpanWriter.write(v1Span, b);
  }
}
