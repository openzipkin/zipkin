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
import java.util.List;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

public final class AsyncSpan2ConsumerAdapter implements AsyncSpanConsumer {

  public static AsyncSpanConsumer create(zipkin.internal.v2.storage.AsyncSpanConsumer delegate) {
    return new AsyncSpan2ConsumerAdapter(delegate);
  }

  final zipkin.internal.v2.storage.AsyncSpanConsumer delegate;

  AsyncSpan2ConsumerAdapter(zipkin.internal.v2.storage.AsyncSpanConsumer delegate) {
    this.delegate = delegate;
  }

  @Override public void accept(List<Span> spans, Callback<Void> callback) {
    int length = spans.size();
    List<Span2> linkSpans = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      linkSpans.addAll(Span2Converter.fromSpan(spans.get(i)));
    }
    delegate.accept(linkSpans, callback);
  }
}
