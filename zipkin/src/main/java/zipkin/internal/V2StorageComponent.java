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
import javax.annotation.Nullable;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.storage.SpanConsumer;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

public abstract class V2StorageComponent implements StorageComponent {
  @Override public final AsyncSpanConsumer asyncSpanConsumer() {
    return new V2AsyncSpanConsumerAdapter(v2AsyncSpanConsumer());
  }

  protected abstract SpanConsumer v2AsyncSpanConsumer();

  static class V2AsyncSpanConsumerAdapter implements AsyncSpanConsumer {
    final SpanConsumer delegate;

    V2AsyncSpanConsumerAdapter(SpanConsumer delegate) {
      this.delegate = delegate;
    }

    @Override public void accept(List<zipkin.Span> spans, Callback<Void> callback) {
      int length = spans.size();
      List<Span> linkSpans = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        linkSpans.addAll(V2SpanConverter.fromSpan(spans.get(i)));
      }
      delegate.accept(linkSpans).enqueue(new V2CallbackAdapter(callback));
    }
  }
}
