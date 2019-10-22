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
