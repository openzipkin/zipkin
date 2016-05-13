/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage;

import java.util.List;
import java.util.concurrent.Executor;
import zipkin.Span;

import static zipkin.internal.Util.checkNotNull;

final class InternalBlockingToAsyncSpanConsumerAdapter implements AsyncSpanConsumer {
  final StorageAdapters.SpanConsumer delegate;
  final Executor executor;

  InternalBlockingToAsyncSpanConsumerAdapter(StorageAdapters.SpanConsumer delegate, Executor executor) {
    this.delegate = checkNotNull(delegate, "delegate");
    this.executor = checkNotNull(executor, "executor");
  }

  @Override public void accept(final List<Span> spans, Callback<Void> callback) {
    executor.execute(new InternalCallbackRunnable<Void>(callback) {
      @Override Void complete() {
        delegate.accept(spans);
        return null;
      }

      @Override public String toString() {
        return "Accept(" + spans + ")";
      }
    });
  }

  @Override public String toString() {
    return delegate.toString();
  }
}
