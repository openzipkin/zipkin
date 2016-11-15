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
package zipkin.storage.elasticsearch.http;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

class CallbackListenableFuture<V> extends AbstractFuture<V> implements okhttp3.Callback {
  final Call call;

  CallbackListenableFuture(Call call) {
    this.call = call;
  }

  @Override public void onFailure(Call call, IOException e) {
    setException(e);
  }

  ListenableFuture<V> enqueue() {
    call.enqueue(this);
    return this;
  }

  @Override protected void interruptTask() {
    call.cancel();
  }

  /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
  @Override public void onResponse(Call call, Response response) {
    try (ResponseBody responseBody = response.body()) {
      if (response.isSuccessful()) {
        set(convert(responseBody));
      } else {
        setException(new IllegalStateException("response failed: " + response));
      }
    } catch (Throwable t) {
      propagateIfFatal(t);
      setException(t);
    }
  }

  // Taken from RxJava, which was taken from scala
  static void propagateIfFatal(Throwable t) {
    if (t instanceof VirtualMachineError) {
      throw (VirtualMachineError) t;
    } else if (t instanceof ThreadDeath) {
      throw (ThreadDeath) t;
    } else if (t instanceof LinkageError) {
      throw (LinkageError) t;
    }
  }

  V convert(ResponseBody responseBody) throws IOException {
    return null;
  }
}
