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
package zipkin2.codec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import zipkin2.Span;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.Nullable;
import zipkin2.internal.Proto3Codec;
import zipkin2.internal.ReadBuffer;
import zipkin2.internal.ThriftCodec;
import zipkin2.internal.V1JsonSpanReader;
import zipkin2.internal.V2SpanReader;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

/** This is separate from {@link SpanBytesEncoder}, as it isn't needed for instrumentation */
@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public enum SpanBytesDecoder implements BytesDecoder<Span> {
  /** Corresponds to the Zipkin v1 json format */
  JSON_V1 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public boolean decode(byte[] span, Collection<Span> out) { // ex DependencyLinker
      Span result = decodeOne(ReadBuffer.wrap(span));
      if (result == null) return false;
      out.add(result);
      return true;
    }

    @Override public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return new V1JsonSpanReader().readList(ReadBuffer.wrap(spans), out);
    }

    @Override public boolean decodeList(ByteBuffer spans, Collection<Span> out) {
      return new V1JsonSpanReader().readList(ReadBuffer.wrapUnsafe(spans), out);
    }

    @Override @Nullable public Span decodeOne(byte[] span) {
      return decodeOne(ReadBuffer.wrap(span));
    }

    @Override @Nullable public Span decodeOne(ByteBuffer span) {
      return decodeOne(ReadBuffer.wrapUnsafe(span));
    }

    Span decodeOne(ReadBuffer buffer) {
      V1Span v1 = JsonCodec.readOne(new V1JsonSpanReader(), buffer);
      List<Span> out = new ArrayList<>(1);
      V1SpanConverter.create().convert(v1, out);
      return out.get(0);
    }

    @Override public List<Span> decodeList(byte[] spans) {
      return doDecodeList(this, spans);
    }

    @Override public List<Span> decodeList(ByteBuffer spans) {
      return doDecodeList(this, spans);
    }
  },
  /** Corresponds to the Zipkin v1 thrift format */
  THRIFT {
    @Override public Encoding encoding() {
      return Encoding.THRIFT;
    }

    @Override public boolean decode(byte[] span, Collection<Span> out) { // ex DependencyLinker
      return ThriftCodec.read(ReadBuffer.wrap(span), out);
    }

    @Override public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return ThriftCodec.readList(ReadBuffer.wrap(spans), out);
    }

    @Override public boolean decodeList(ByteBuffer spans, Collection<Span> out) {
      return ThriftCodec.readList(ReadBuffer.wrapUnsafe(spans), out);
    }

    @Override @Nullable public Span decodeOne(byte[] span) {
      return ThriftCodec.readOne(ReadBuffer.wrap(span));
    }

    @Override @Nullable public Span decodeOne(ByteBuffer span) {
      return ThriftCodec.readOne(ReadBuffer.wrapUnsafe(span));
    }

    @Override public List<Span> decodeList(byte[] spans) {
      return doDecodeList(this, spans);
    }

    @Override public List<Span> decodeList(ByteBuffer spans) {
      return doDecodeList(this, spans);
    }
  },
  /** Corresponds to the Zipkin v2 json format */
  JSON_V2 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public boolean decode(byte[] span, Collection<Span> out) { // ex DependencyLinker
      return JsonCodec.read(new V2SpanReader(), ReadBuffer.wrap(span), out);
    }

    @Override public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return JsonCodec.readList(new V2SpanReader(), ReadBuffer.wrap(spans), out);
    }

    @Override public boolean decodeList(ByteBuffer spans, Collection<Span> out) {
      return JsonCodec.readList(new V2SpanReader(), ReadBuffer.wrapUnsafe(spans), out);
    }

    @Override @Nullable public Span decodeOne(byte[] span) {
      return JsonCodec.readOne(new V2SpanReader(), ReadBuffer.wrap(span));
    }

    @Override @Nullable public Span decodeOne(ByteBuffer span) {
      return JsonCodec.readOne(new V2SpanReader(), ReadBuffer.wrapUnsafe(span));
    }

    @Override public List<Span> decodeList(byte[] spans) {
      return doDecodeList(this, spans);
    }

    @Override public List<Span> decodeList(ByteBuffer spans) {
      return doDecodeList(this, spans);
    }
  },
  PROTO3 {
    @Override public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override public boolean decode(byte[] span, Collection<Span> out) { // ex DependencyLinker
      return Proto3Codec.read(ReadBuffer.wrap(span), out);
    }

    @Override public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return Proto3Codec.readList(ReadBuffer.wrap(spans), out);
    }

    @Override public boolean decodeList(ByteBuffer spans, Collection<Span> out) {
      return Proto3Codec.readList(ReadBuffer.wrapUnsafe(spans), out);
    }

    @Override @Nullable public Span decodeOne(byte[] span) {
      return Proto3Codec.readOne(ReadBuffer.wrap(span));
    }

    @Override @Nullable public Span decodeOne(ByteBuffer span) {
      return Proto3Codec.readOne(ReadBuffer.wrapUnsafe(span));
    }

    @Override public List<Span> decodeList(byte[] spans) {
      return doDecodeList(this, spans);
    }

    @Override public List<Span> decodeList(ByteBuffer spans) {
      return doDecodeList(this, spans);
    }
  };

  /**
   * ByteBuffer implementation of {@link #decodeList(byte[])}.
   *
   * <p>Note: only use this when it is ok to modify the underlying {@link ByteBuffer#array()}.
   */
  public abstract boolean decodeList(ByteBuffer spans, Collection<Span> out);

  /**
   * ByteBuffer implementation of {@link #decodeList(byte[])}.
   *
   * <p>Note: only use this when it is ok to modify the underlying {@link ByteBuffer#array()}.
   */
  public abstract List<Span> decodeList(ByteBuffer spans);

  /**
   * ByteBuffer implementation of {@link #decodeOne(byte[])}
   *
   * <p>Note: only use this when it is ok to modify the underlying {@link ByteBuffer#array()}.
   */
  @Nullable public abstract Span decodeOne(ByteBuffer span);

  static List<Span> doDecodeList(SpanBytesDecoder decoder, byte[] spans) {
    List<Span> out = new ArrayList<>();
    decoder.decodeList(spans, out);
    return out;
  }

  static List<Span> doDecodeList(SpanBytesDecoder decoder, ByteBuffer spans) {
    List<Span> out = new ArrayList<>();
    decoder.decodeList(spans, out);
    return out;
  }
}
