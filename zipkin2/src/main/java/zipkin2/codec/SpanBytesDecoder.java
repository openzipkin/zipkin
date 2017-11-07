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
package zipkin2.codec;

import java.util.Collection;
import java.util.List;
import zipkin2.Span;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.Nullable;
import zipkin2.internal.V2SpanReader;

/** This is separate from {@link SpanBytesEncoder}, as it isn't needed for instrumentation */
@SuppressWarnings("ImmutableEnumChecker") // because span is immutable
public enum SpanBytesDecoder implements BytesDecoder<Span> {
  /** Corresponds to the Zipkin v2 json format */
  JSON_V2 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override
    public boolean decode(byte[] span, Collection<Span> out) { // ex decode span in dependencies job
      return JsonCodec.read(new V2SpanReader(), span, out);
    }

    @Override public boolean decodeList(byte[] spans, Collection<Span> out) { // ex getTrace
      return JsonCodec.readList(new V2SpanReader(), spans, out);
    }

    /** Visible for testing. This returns the first span parsed from the serialized object or null */
    @Override @Nullable public Span decodeOne(byte[] span) {
      return JsonCodec.readOne(new V2SpanReader(), span);
    }

    /** Convenience method for {@link #decode(byte[], Collection)} */
    @Override public List<Span> decodeList(byte[] spans) {
      return JsonCodec.readList(new V2SpanReader(), spans);
    }
  }
}
