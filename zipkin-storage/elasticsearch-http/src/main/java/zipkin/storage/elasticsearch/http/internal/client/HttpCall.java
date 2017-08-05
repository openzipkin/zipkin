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
package zipkin.storage.elasticsearch.http.internal.client;

import java.io.Closeable;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.BufferedSource;
import okio.GzipSource;
import okio.Okio;
import zipkin.internal.CallbackCaptor;
import zipkin.storage.Callback;

import static zipkin.internal.Util.propagateIfFatal;

public final class HttpCall<V> {

  public interface BodyConverter<V> {
    V convert(BufferedSource content) throws IOException;
  }

  public static class Factory implements Closeable {
    final OkHttpClient ok;
    public final HttpUrl baseUrl;

    public Factory(OkHttpClient ok, HttpUrl baseUrl) {
      this.ok = ok;
      this.baseUrl = baseUrl;
    }

    public <V> HttpCall<V> newCall(Request request, BodyConverter<V> bodyConverter) {
      return new HttpCall(this, request, bodyConverter);
    }

    public <V> V execute(Request request, BodyConverter<V> bodyConverter) {
      CallbackCaptor<V> response = new CallbackCaptor<>();
      newCall(request, bodyConverter).submit(response);
      return response.get();
    }

    @Override public void close() {
      ok.dispatcher().executorService().shutdownNow();
    }
  }

  final Call.Factory ok;
  final Request request;
  final BodyConverter<V> bodyConverter;

  HttpCall(Factory factory, Request request, BodyConverter<V> bodyConverter) {
    this.ok = factory.ok;
    this.request = request;
    this.bodyConverter = bodyConverter;
  }

  public void submit(Callback<V> delegate) {
    ok.newCall(request).enqueue(new CallbackAdapter<>(bodyConverter, delegate));
  }

  static class CallbackAdapter<V> implements okhttp3.Callback {
    final BodyConverter<V> bodyConverter;
    final Callback<V> delegate;

    CallbackAdapter(BodyConverter<V> bodyConverter, Callback<V> delegate) {
      this.bodyConverter = bodyConverter;
      this.delegate = delegate;
    }

    @Override public void onFailure(Call call, IOException e) {
      delegate.onError(e);
    }

    /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
    @Override public void onResponse(Call call, Response response) {
      if (!HttpHeaders.hasBody(response)) {
        if (response.isSuccessful()) {
          delegate.onSuccess(null);
        } else {
          delegate.onError(new IllegalStateException("response failed: " + response));
        }
        return;
      }
      try (ResponseBody responseBody = response.body()) {
        BufferedSource content = responseBody.source();
        if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) {
          content = Okio.buffer(new GzipSource(responseBody.source()));
        }
        if (response.isSuccessful()) {
          delegate.onSuccess(bodyConverter.convert(content));
        } else {
          delegate.onError(new IllegalStateException(
            "response for " + response.request().tag() + " failed: " + content.readUtf8()));
        }
      } catch (Throwable t) {
        propagateIfFatal(t);
        delegate.onError(t);
      }
    }
  }
}
