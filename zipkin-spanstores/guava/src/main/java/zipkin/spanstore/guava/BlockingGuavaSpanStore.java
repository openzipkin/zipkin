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
package zipkin.spanstore.guava;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.internal.Nullable;

import static com.google.common.util.concurrent.Futures.getUnchecked;

/**
 * A {@link SpanStore} implementation that can take a {@link GuavaSpanStore} and call its methods
 * with blocking, for use in callers that need a normal {@link SpanStore}.
 */
public class BlockingGuavaSpanStore implements SpanStore {
  /**
   * Internal flag that allows you read-your-writes consistency during tests.
   *
   * <p>This is internal as collection endpoints are usually in different threads or not in the same
   * process as query ones. Special-casing this allows tests to pass without changing {@link
   * GuavaSpanConsumer#accept}.
   *
   * <p>Why not just change {@link GuavaSpanConsumer#accept} now? {@link GuavaSpanConsumer#accept}
   * may indeed need to change, but when that occurs, we'd want to choose something that is widely
   * supportable, and serving a specific use case. That api might not be a future, for example.
   * Future is difficult, for example, properly supporting and testing cancel. Further, there are
   * other async models such as callbacks that could be more supportable. Regardless, this work is
   * best delayed until there's a worthwhile use-case vs up-fronting only due to tests, and
   * prematurely choosing Future results.
   */
  @VisibleForTesting
  public static boolean BLOCK_ON_ACCEPT;

  private final GuavaSpanStore delegate;

  public BlockingGuavaSpanStore(GuavaSpanStore delegate) {
    this.delegate = delegate;
  }

  // Only method that does not actually block even in synchronous spanstores.
  @Override public void accept(List<Span> spans) {
    ListenableFuture<Void> future = delegate.accept(spans);
    if (BLOCK_ON_ACCEPT) {
      getUnchecked(future);
    }
  }

  @Override public List<List<Span>> getTraces(QueryRequest request) {
    return getUnchecked(delegate.getTraces(request));
  }

  @Override public List<Span> getTrace(long id) {
    return getUnchecked(delegate.getTrace(id));
  }

  @Override public List<Span> getRawTrace(long traceId) {
    return getUnchecked(delegate.getRawTrace(traceId));
  }

  @Override public List<String> getServiceNames() {
    return getUnchecked(delegate.getServiceNames());
  }

  @Override public List<String> getSpanNames(String serviceName) {
    return getUnchecked(delegate.getSpanNames(serviceName));
  }

  @Override public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    return getUnchecked(delegate.getDependencies(endTs, lookback));
  }
}
