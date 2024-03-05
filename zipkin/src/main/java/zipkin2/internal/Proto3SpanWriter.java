/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.List;
import zipkin2.Span;

import static zipkin2.internal.Proto3Fields.sizeOfLengthDelimitedField;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;

//@Immutable
final class Proto3SpanWriter implements WriteBuffer.Writer<Span> {

  static final byte[] EMPTY_ARRAY = new byte[0];

  @Override public int sizeInBytes(Span span) {
    return SPAN.sizeInBytes(span);
  }

  @Override public void write(Span value, WriteBuffer b) {
    SPAN.write(b, value);
  }

  @Override public String toString() {
    return "Span";
  }

  /** Encodes per ListOfSpans data wireType, where field one is repeated spans */
  public byte[] writeList(List<Span> spans) {
    int lengthOfSpans = spans.size();
    if (lengthOfSpans == 0) return EMPTY_ARRAY;
    if (lengthOfSpans == 1) return write(spans.get(0));

    int sizeInBytes = 0;
    int[] sizeOfValues = new int[lengthOfSpans];
    for (int i = 0; i < lengthOfSpans; i++) {
      int sizeOfValue = sizeOfValues[i] = SPAN.sizeOfValue(spans.get(i));
      sizeInBytes += sizeOfLengthDelimitedField(sizeOfValue);
    }
    byte[] result = new byte[sizeInBytes];
    WriteBuffer writeBuffer = WriteBuffer.wrap(result);
    for (int i = 0; i < lengthOfSpans; i++) {
      writeSpan(spans.get(i), sizeOfValues[i], writeBuffer);
    }
    return result;
  }

  byte[] write(Span onlySpan) {
    int sizeOfValue = SPAN.sizeOfValue(onlySpan);
    byte[] result = new byte[sizeOfLengthDelimitedField(sizeOfValue)];
    writeSpan(onlySpan, sizeOfValue, WriteBuffer.wrap(result));
    return result;
  }

  // prevents resizing twice
  void writeSpan(Span span, int sizeOfSpan, WriteBuffer result) {
    result.writeByte(SPAN.key);
    result.writeVarint(sizeOfSpan); // length prefix
    SPAN.writeValue(result, span);
  }

  int writeList(List<Span> spans, byte[] out, int pos) {
    int lengthOfSpans = spans.size();
    if (lengthOfSpans == 0) return 0;

    WriteBuffer result = WriteBuffer.wrap(out, pos);
    for (Span span : spans) {
      SPAN.write(result, span);
    }
    return result.pos() - pos;
  }
}
