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

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;

/**
 * An interface that is equivalent to {@link SpanStore} but exposes methods as
 * {@link ListenableFuture} to allow asynchronous composition.
 *
 * @see SpanStore
 */
public interface GuavaSpanStore {

  /**
   * Version of {@link SpanStore#getTraces} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<List<Span>>> getTraces(QueryRequest request);

  /**
   * @deprecated Please switch to {@link #getTrace(long, long)}
   */
  @Deprecated
  ListenableFuture<List<Span>> getTrace(long id);

  /**
   * Version of {@link SpanStore#getTrace(long, long)} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<Span>> getTrace(long traceIdHigh, long traceIdLow);

  /**
   * @deprecated Please switch to {@link #getRawTrace(long, long)}
   */
  @Deprecated
  ListenableFuture<List<Span>> getRawTrace(long traceId);

  /**
   * Version of {@link SpanStore#getRawTrace(long, long)} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<Span>> getRawTrace(long traceIdHigh, long traceIdLow);

  /**
   * Version of {@link SpanStore#getServiceNames} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<String>> getServiceNames();

  /**
   * Version of {@link SpanStore#getSpanNames} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<String>> getSpanNames(String serviceName);

  /**
   * Version of {@link SpanStore#getDependencies} that returns {@link ListenableFuture}.
   */
  ListenableFuture<List<DependencyLink>> getDependencies(long endTs, @Nullable Long lookback);
}
