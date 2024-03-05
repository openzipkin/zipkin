/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.Collection;
import java.util.List;
import zipkin2.Span;

import static java.lang.String.format;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;

// @Immutable
public final class Proto3Codec {

  final Proto3SpanWriter writer = new Proto3SpanWriter();

  public int sizeInBytes(Span input) {
    return writer.sizeInBytes(input);
  }

  public byte[] write(Span span) {
    return writer.write(span);
  }

  public byte[] writeList(List<Span> spans) {
    return writer.writeList(spans);
  }

  public int writeList(List<Span> spans, byte[] out, int pos) {
    return writer.writeList(spans, out, pos);
  }

  public static boolean read(ReadBuffer buffer, Collection<Span> out) {
    if (buffer.available() == 0) return false;
    try {
      Span span = SPAN.read(buffer);
      if (span == null) return false;
      out.add(span);
      return true;
    } catch (RuntimeException e) {
      throw exceptionReading("Span", e);
    }
  }

  public static @Nullable Span readOne(ReadBuffer buffer) {
    try {
      return SPAN.read(buffer);
    } catch (RuntimeException e) {
      throw exceptionReading("Span", e);
    }
  }

  public static boolean readList(ReadBuffer buffer, Collection<Span> out) {
    int length = buffer.available();
    if (length == 0) return false;
    try {
      while (buffer.pos() < length) {
        Span span = SPAN.read(buffer);
        if (span == null) return false;
        out.add(span);
      }
    } catch (RuntimeException e) {
      throw exceptionReading("List<Span>", e);
    }
    return true;
  }

  static IllegalArgumentException exceptionReading(String type, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.contains("Malformed")) cause = "Malformed";
    String message = format("%s reading %s from proto3", cause, type);
    throw new IllegalArgumentException(message, e);
  }
}
