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

import com.google.common.util.concurrent.FutureCallback;
import zipkin.internal.Nullable;
import zipkin.storage.Callback;

import static zipkin.internal.Util.checkNotNull;

final class InternalForwardingCallback<T> implements FutureCallback<T> {
  final Callback<T> delegate;

  InternalForwardingCallback(Callback<T> delegate) {
    this.delegate = checkNotNull(delegate, "callback");
  }

  @Override public void onSuccess(@Nullable T t) {
    delegate.onSuccess(t);
  }

  @Override public void onFailure(Throwable throwable) {
    delegate.onError(throwable);
  }

  @Override public String toString() {
    return delegate.toString();
  }
}
