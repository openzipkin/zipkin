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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;
import zipkin.storage.Callback;

import static zipkin.internal.Util.propagateIfFatal;

class CallbackAdapter<V> implements okhttp3.Callback {
  final Call call;
  final Callback<V> delegate;

  CallbackAdapter(Call call, zipkin.storage.Callback<V> delegate) {
    this.call = call;
    this.delegate = delegate;
  }

  @Override public void onFailure(Call call, IOException e) {
    delegate.onError(e);
  }

  void enqueue() {
    call.enqueue(this);
  }

  /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
  @Override public void onResponse(Call call, Response response) {
    try (ResponseBody responseBody = response.body()) {
      if (response.isSuccessful()) {
        delegate.onSuccess(convert(responseBody));
      } else {
        delegate.onError(new IllegalStateException("response failed: " + response));
      }
    } catch (Throwable t) {
      propagateIfFatal(t);
      delegate.onError(t);
    }
  }

  V convert(ResponseBody responseBody) throws IOException {
    return null;
  }
}
