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
package zipkin.storage;

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;

/**
 * An interface that is equivalent to {@link SpanStore} but accepts callbacks to allow
 * bridging to async libraries.
 *
 * <p>Note: This is not considered a user-level Api, rather an Spi that can be used to bind
 * user-level abstractions such as futures or observables.
 *
 * @see SpanStore
 */
public interface AsyncSpanStore {

  /**
   * Version of {@link SpanStore#getTraces} that accepts {@link Callback}.
   */
  void getTraces(QueryRequest request, Callback<List<List<Span>>> callback);

  /**
   * @deprecated Please switch to {@link #getTrace(long, long, Callback)}
   */
  @Deprecated
  void getTrace(long id, Callback<List<Span>> callback);

  /**
   * Version of {@link SpanStore#getTrace(long, long)} that accepts {@link Callback}.
   */
  void getTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback);

  /**
   * @deprecated Please switch to {@link #getRawTrace(long, long, Callback)}
   */
  @Deprecated
  void getRawTrace(long traceId, Callback<List<Span>> callback);

  /**
   * Version of {@link SpanStore#getRawTrace(long, long)} that accepts {@link Callback}.
   */
  void getRawTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback);

  /**
   * Version of {@link SpanStore#getServiceNames} that accepts {@link Callback}.
   */
  void getServiceNames(Callback<List<String>> callback);

  /**
   * Version of {@link SpanStore#getSpanNames} that accepts {@link Callback}.
   */
  void getSpanNames(String serviceName, Callback<List<String>> callback);

  /**
   * Version of {@link SpanStore#getDependencies} that accepts {@link Callback}.
   */
  void getDependencies(long endTs, @Nullable Long lookback,
      Callback<List<DependencyLink>> callback);
}
