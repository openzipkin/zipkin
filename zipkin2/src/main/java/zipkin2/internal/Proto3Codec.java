/**
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
package zipkin2.internal;

import java.util.List;
import zipkin2.Span;

//@Immutable
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
}
