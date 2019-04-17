/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.Limiter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

/**
 * Delegating implementation that wraps another {@link SpanConsumer} and ensures that only so many requests
 * can get through to it at a given time.
 *
 * @see ThrottledCall
 */
final class ThrottledSpanConsumer implements SpanConsumer {
  final SpanConsumer delegate;
  final Limiter<Void> limiter;
  final ExecutorService executor;

  ThrottledSpanConsumer(SpanConsumer delegate, Limiter<Void> limiter, ExecutorService executor) {
    this.delegate = delegate;
    this.limiter = limiter;
    this.executor = executor;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    return new ThrottledCall<>(executor, limiter, () -> delegate.accept(spans));
  }
}
