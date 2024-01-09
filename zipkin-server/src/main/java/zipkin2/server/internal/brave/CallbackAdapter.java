/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
package zipkin2.server.internal.brave;

import zipkin2.reporter.Callback;

// exposed for tests
public final class CallbackAdapter<V> implements zipkin2.Callback<V> {
  private final Callback<V> delegate;

  public CallbackAdapter(Callback<V> delegate) {
    this.delegate = delegate;
  }

  @Override public void onSuccess(V value) {
    delegate.onSuccess(value);
  }

  @Override public void onError(Throwable t) {
    delegate.onError(t);
  }
}
