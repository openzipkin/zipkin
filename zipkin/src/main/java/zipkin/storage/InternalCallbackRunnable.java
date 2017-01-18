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

import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.propagateIfFatal;

abstract class InternalCallbackRunnable<V> implements Runnable {
  final Callback<V> callback;

  protected InternalCallbackRunnable(Callback<V> callback) {
    this.callback = checkNotNull(callback, "callback");
  }

  abstract V complete();

  @Override public void run() {
    try {
      callback.onSuccess(complete());
    } catch (Throwable t) {
      propagateIfFatal(t);
      callback.onError(t);
    }
  }
}
