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

import com.google.common.collect.ForwardingObject;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.async.AsyncSpanConsumer;
import zipkin.async.AsyncSpanStore;
import zipkin.async.Callback;
import zipkin.internal.Nullable;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static zipkin.internal.Util.checkNotNull;

/**
 * A {@link AsyncSpanStore} derived from an {@link GuavaSpanStore}. Used to adapt to other
 * composition libraries.
 *
 * <p>In implementation, this adds the zipkin callback to the future's listeners.
 */
public abstract class GuavaToAsyncSpanStoreAdapter extends ForwardingObject
    implements AsyncSpanStore, AsyncSpanConsumer {
  protected static final ListenableFuture<List<String>> EMPTY_LIST =
      immediateFuture(Collections.<String>emptyList());
  protected static final Ordering<List<Span>> TRACE_DESCENDING = Ordering.from(new Comparator<List<Span>>() {
    @Override
    public int compare(List<Span> left, List<Span> right) {
      return right.get(0).compareTo(left.get(0));
    }
  });

  protected GuavaToAsyncSpanStoreAdapter() {
  }

  /**
   * Allows this adapter to be extended by a {@link GuavaSpanStore}, and not interfere with its
   * constructor.
   */
  @Override
  protected abstract GuavaSpanStore delegate();

  @Override
  public void accept(List<Span> spans, Callback<Void> callback) {
    addCallback(delegate().accept(spans), new ForwardingCallback<>(callback));
  }

  @Override public void getTraces(QueryRequest request, Callback<List<List<Span>>> callback) {
    addCallback(delegate().getTraces(request), new ForwardingCallback<>(callback));
  }

  @Override public void getTrace(long id, Callback<List<Span>> callback) {
    addCallback(delegate().getTrace(id), new ForwardingCallback<>(callback));
  }

  @Override public void getRawTrace(long traceId, Callback<List<Span>> callback) {
    addCallback(delegate().getRawTrace(traceId), new ForwardingCallback<>(callback));
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    addCallback(delegate().getServiceNames(), new ForwardingCallback<>(callback));
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    addCallback(delegate().getSpanNames(serviceName), new ForwardingCallback<>(callback));
  }

  @Override public void getDependencies(long endTs, @Nullable Long lookback,
      Callback<List<DependencyLink>> callback) {
    addCallback(delegate().getDependencies(endTs, lookback), new ForwardingCallback<>(callback));
  }

  static final class ForwardingCallback<T> extends ForwardingObject implements FutureCallback<T> {
    final Callback<T> delegate;

    ForwardingCallback(Callback<T> delegate) {
      this.delegate = checkNotNull(delegate, "callback");
    }

    @Override public void onSuccess(T t) {
      delegate().onSuccess(t);
    }

    @Override public void onFailure(Throwable throwable) {
      delegate().onError(throwable);
    }

    @Override protected Callback<T> delegate() {
      return delegate;
    }
  }
}
