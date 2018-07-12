/*
 * Copyright 2015-2018 The OpenZipkin Authors
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import zipkin2.Span;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.Nullable;
import zipkin2.internal.Proto3Codec;
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
    @Override
    public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override
    public boolean decode(byte[] bytes, Collection<Span> out) {
      Span result = decodeOne(bytes);
      if (result == null) return false;
      out.add(result);
      return true;
    }

    @Override
    public boolean decodeList(byte[] spans, Collection<Span> out) {
      return new V1JsonSpanReader().readList(spans, out);
    }

    @Override
    public Span decodeOne(byte[] span) {
      V1Span v1 = JsonCodec.readOne(new V1JsonSpanReader(), span);
      List<Span> out = new ArrayList<>(1);
      V1SpanConverter.create().convert(v1, out);
      return out.get(0);
    }

    @Override
    public List<Span> decodeList(byte[] spans) {
      return decodeList(this, spans);
    }
  },
  /** Corresponds to the Zipkin v1 thrift format */
  THRIFT {
    @Override
    public Encoding encoding() {
      return Encoding.THRIFT;
    }

    @Override
    public boolean decode(byte[] span, Collection<Span> out) {
      return ThriftCodec.read(span, out);
    }

    @Override
    public boolean decodeList(byte[] spans, Collection<Span> out) {
      return ThriftCodec.readList(spans, out);
    }

    @Override
    public Span decodeOne(byte[] span) {
      return ThriftCodec.readOne(span);
    }

    @Override
    public List<Span> decodeList(byte[] spans) {
      return decodeList(this, spans);
    }
  },
  /** Corresponds to the Zipkin v2 json format */
  JSON_V2 {
    @Override
    public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override
    public boolean decode(byte[] span, Collection<Span> out) { // ex decode span in dependencies job
      return JsonCodec.read(new V2SpanReader(), span, out);
    }

    @Override
    public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return JsonCodec.readList(new V2SpanReader(), spans, out);
    }

    @Override
    @Nullable
    public Span decodeOne(byte[] span) {
      return JsonCodec.readOne(new V2SpanReader(), span);
    }

    @Override
    public List<Span> decodeList(byte[] spans) {
      return decodeList(this, spans);
    }
  },
  PROTO3 {
    @Override
    public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override
    public boolean decode(byte[] span, Collection<Span> out) { // ex decode span in dependencies job
      return Proto3Codec.read(span, out);
    }

    @Override
    public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return Proto3Codec.readList(spans, out);
    }

    @Override
    @Nullable
    public Span decodeOne(byte[] span) {
      return Proto3Codec.readOne(span);
    }

    @Override
    public List<Span> decodeList(byte[] spans) {
      return decodeList(this, spans);
    }
  };

  static List<Span> decodeList(SpanBytesDecoder decoder, byte[] spans) {
    List<Span> out = new ArrayList<>();
    if (!decoder.decodeList(spans, out)) return Collections.emptyList();
    return out;
  }
}
