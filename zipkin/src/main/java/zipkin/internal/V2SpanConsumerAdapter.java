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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

final class V2SpanConsumerAdapter implements AsyncSpanConsumer {
  final SpanConsumer delegate;

  V2SpanConsumerAdapter(SpanConsumer delegate) {
    this.delegate = delegate;
  }

  @Override public void accept(List<zipkin.Span> spans, Callback<Void> callback) {
    delegate.accept(fromSpans(spans)).enqueue(new V2CallbackAdapter<>(callback));
  }

  static List<Span> fromSpans(List<zipkin.Span> spans) {
    if (spans.isEmpty()) return Collections.emptyList();
    int length = spans.size();
    List<Span> span2s = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      span2s.addAll(V2SpanConverter.fromSpan(spans.get(i)));
    }
    return span2s;
  }
}
