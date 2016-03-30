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

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.internal.Nullable;

/**
 * An interface that is equivalent to {@link zipkin.SpanStore} but exposes methods as
 * {@link ListenableFuture} to allow asynchronous composition.
 *
 * @see zipkin.SpanStore
 */
public interface GuavaSpanStore extends GuavaAsyncSpanConsumer {

  /**
   * Version of {@link zipkin.SpanStore#getTraces} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<List<Span>>> getTraces(QueryRequest request);

  /**
   * Version of {@link zipkin.SpanStore#getTrace} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<Span>> getTrace(long id);

  /**
   * Version of {@link zipkin.SpanStore#getRawTrace} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<Span>> getRawTrace(long traceId);

  /**
   * Version of {@link zipkin.SpanStore#getServiceNames} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<String>> getServiceNames();

  /**
   * Version of {@link zipkin.SpanStore#getSpanNames} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<String>> getSpanNames(String serviceName);

  /**
   * Version of {@link zipkin.SpanStore#getDependencies} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<DependencyLink>> getDependencies(long endTs, @Nullable Long lookback);
}
