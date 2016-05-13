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
package zipkin.storage.interop;

import com.twitter.util.Promise;
import com.twitter.util.Promise$;
import zipkin.storage.Callback;
import zipkin.internal.Nullable;

/**
 * This callback takes an input java type and converts it to a scala type on success.
 *
 * @param <J> java type of the callback
 * @param <S> scala type of the promise
 */
abstract class CallbackWithPromise<J, S> implements Callback<J> {
  final Promise<S> promise = Promise$.MODULE$.apply();

  protected abstract S convertToScala(J input);

  @Override public void onSuccess(@Nullable J value) {
    promise.setValue(convertToScala(value));
  }

  @Override public void onError(Throwable t) {
    promise.setException(t);
  }
}