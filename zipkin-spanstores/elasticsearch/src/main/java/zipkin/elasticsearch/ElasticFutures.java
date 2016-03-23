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

package zipkin.elasticsearch;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ListenableActionFuture;
import zipkin.async.Callback;

final class ElasticFutures {

  static <T> ListenableFuture<T> toGuava(ListenableActionFuture<T> elasticFuture) {
    final SettableFuture<T> future = SettableFuture.create();
    elasticFuture.addListener(new ActionListener<T>() {
      @Override public void onResponse(T t) {
        future.set(t);
      }

      @Override public void onFailure(Throwable e) {
        future.setException(e);
      }
    });
    return future;
  }

  static <A, C> void onComplete(
      ListenableActionFuture<A> future,
      final Callback<C> callback,
      final Function<A, C> successTransformationFn
  ) {
    future.addListener(new ActionListener<A>() {

      @Override public void onResponse(A a) {
        try {
          callback.onSuccess(successTransformationFn.apply(a));
        } catch (Error | RuntimeException e) {
          callback.onError(e);
        }
      }

      @Override public void onFailure(Throwable e) {
        callback.onError(e);
      }
    });
  }

  static <A, C> void onComplete(
      ListenableFuture<A> future,
      final Callback<C> callback,
      final Function<A, C> successTransformationFn
  ) {
    Futures.addCallback(future, new FutureCallback<A>() {

      @Override public void onSuccess(A result) {
        try {
          callback.onSuccess(successTransformationFn.apply(result));
        } catch (Error | RuntimeException e) {
          callback.onError(e);
        }
      }

      @Override public void onFailure(Throwable t) {
        callback.onError(t);
      }
    });
  }
}
