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
package zipkin.storage.guava;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import static zipkin.internal.Util.checkNotNull;

/**
 * A {@link GuavaSpanConsumer} derived from an {@link AsyncSpanConsumer}. Used by callers who prefer
 * to compose futures.
 */
final class InternalGuavaSpanConsumerAdapter implements GuavaSpanConsumer {
  final AsyncSpanConsumer delegate;

  InternalGuavaSpanConsumerAdapter(AsyncSpanConsumer delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
  }

  @Override public ListenableFuture<Void> accept(List<Span> spans) {
    VoidListenableFuture result = new VoidListenableFuture();
    delegate.accept(spans, result);
    return result;
  }

  @Override public String toString() {
    return delegate.toString();
  }

  static final class VoidListenableFuture extends AbstractFuture<Void> implements Callback<Void> {
    @Override public void onSuccess(@Nullable Void value) {
      set(value);
    }

    @Override public void onError(Throwable t) {
      setException(t);
    }
  }
}
