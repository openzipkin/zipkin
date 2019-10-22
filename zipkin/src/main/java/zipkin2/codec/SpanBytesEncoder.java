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

import java.util.List;
import zipkin2.Span;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.Proto3Codec;
import zipkin2.internal.V1JsonSpanWriter;
import zipkin2.internal.V1ThriftSpanWriter;
import zipkin2.internal.V2SpanWriter;

/** Limited interface needed by those writing span reporters */
@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public enum SpanBytesEncoder implements BytesEncoder<Span> {
  /** Corresponds to the Zipkin v1 json format (with tags as binary annotations) */
  JSON_V1 {
    @Override
    public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override
    public int sizeInBytes(Span input) {
      return new V1JsonSpanWriter().sizeInBytes(input);
    }

    @Override
    public byte[] encode(Span span) {
      return JsonCodec.write(new V1JsonSpanWriter(), span);
    }

    @Override
    public byte[] encodeList(List<Span> spans) {
      return JsonCodec.writeList(new V1JsonSpanWriter(), spans);
    }

    @Override
    public int encodeList(List<Span> spans, byte[] out, int pos) {
      return JsonCodec.writeList(new V1JsonSpanWriter(), spans, out, pos);
    }
  },
  /** Corresponds to the Zipkin v1 thrift format */
  THRIFT {
    @Override
    public Encoding encoding() {
      return Encoding.THRIFT;
    }

    @Override
    public int sizeInBytes(Span input) {
      return new V1ThriftSpanWriter().sizeInBytes(input);
    }

    @Override
    public byte[] encode(Span span) {
      return new V1ThriftSpanWriter().write(span);
    }

    @Override
    public byte[] encodeList(List<Span> spans) {
      return new V1ThriftSpanWriter().writeList(spans);
    }

    @Override
    public int encodeList(List<Span> spans, byte[] out, int pos) {
      return new V1ThriftSpanWriter().writeList(spans, out, pos);
    }
  },
  /** Corresponds to the Zipkin v2 json format */
  JSON_V2 {
    final V2SpanWriter writer = new V2SpanWriter();

    @Override
    public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override
    public int sizeInBytes(Span input) {
      return writer.sizeInBytes(input);
    }

    @Override
    public byte[] encode(Span span) {
      return JsonCodec.write(writer, span);
    }

    @Override
    public byte[] encodeList(List<Span> spans) {
      return JsonCodec.writeList(writer, spans);
    }

    @Override
    public int encodeList(List<Span> spans, byte[] out, int pos) {
      return JsonCodec.writeList(writer, spans, out, pos);
    }
  },
  PROTO3 {
    final Proto3Codec codec = new Proto3Codec();

    @Override
    public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override
    public int sizeInBytes(Span input) {
      return codec.sizeInBytes(input);
    }

    @Override
    public byte[] encode(Span span) {
      return codec.write(span);
    }

    @Override
    public byte[] encodeList(List<Span> spans) {
      return codec.writeList(spans);
    }

    @Override
    public int encodeList(List<Span> spans, byte[] out, int pos) {
      return codec.writeList(spans, out, pos);
    }
  };

  /** Allows you to encode a list of spans onto a specific offset. For example, when nesting */
  public abstract int encodeList(List<Span> spans, byte[] out, int pos);
}
