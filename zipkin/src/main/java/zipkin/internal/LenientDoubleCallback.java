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
package zipkin.internal;

import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin.storage.Callback;

/** Callback that succeeds if at least one value does. The first error is logged. */
abstract class LenientDoubleCallback<V> implements Callback<V> {
  final Logger log;
  final Callback<V> delegate;

  /** this differentiates between not yet set and null */
  boolean vSet;
  V v;
  Throwable t;

  LenientDoubleCallback(Logger log, Callback<V> delegate) {
    this.log = log;
    this.delegate = delegate;
  }

  abstract @Nullable V merge(@Nullable V v1, @Nullable V v2);

  @Override synchronized final public void onSuccess(@Nullable V value) {
    if (t != null) {
      delegate.onSuccess(value);
    } else if (!vSet) {
      v = value;
      vSet = true;
    } else {
      delegate.onSuccess(merge(v, value));
    }
  }

  @Override synchronized final public void onError(Throwable throwable) {
    if (vSet) {
      delegate.onSuccess(v);
    } else if (t == null) {
      log.log(Level.INFO, "first error", throwable);
      t = throwable;
    } else {
      delegate.onError(throwable);
    }
  }
}
