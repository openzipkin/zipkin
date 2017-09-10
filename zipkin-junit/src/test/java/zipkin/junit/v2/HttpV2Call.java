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
package zipkin.junit.v2;

import java.io.Closeable;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.BufferedSource;
import okio.GzipSource;
import okio.Okio;
import zipkin2.Call;
import zipkin2.Callback;

public final class HttpV2Call<V> extends Call<V> {

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

    public <V> HttpV2Call<V> newCall(Request request, BodyConverter<V> bodyConverter) {
      return new HttpV2Call<>(this, request, bodyConverter);
    }

    @Override public void close() {
      ok.dispatcher().executorService().shutdownNow();
    }
  }

  final okhttp3.Call call;
  final BodyConverter<V> bodyConverter;

  HttpV2Call(Factory factory, Request request, BodyConverter<V> bodyConverter) {
    this(factory.ok.newCall(request), bodyConverter);
  }

  HttpV2Call(okhttp3.Call call, BodyConverter<V> bodyConverter) {
    this.call = call;
    this.bodyConverter = bodyConverter;
  }

  @Override public V execute() throws IOException {
    return parseResponse(call.execute(), bodyConverter);
  }

  @Override public void enqueue(Callback<V> delegate) {
    call.enqueue(new CallbackAdapter<>(bodyConverter, delegate));
  }

  @Override public void cancel() {
    call.cancel();
  }

  @Override public boolean isCanceled() {
    return call.isCanceled();
  }

  @Override public HttpV2Call<V> clone() {
    return new HttpV2Call<>(call.clone(), bodyConverter);
  }

  static class CallbackAdapter<V> implements okhttp3.Callback {
    final BodyConverter<V> bodyConverter;
    final Callback<V> delegate;

    CallbackAdapter(BodyConverter<V> bodyConverter, Callback<V> delegate) {
      this.bodyConverter = bodyConverter;
      this.delegate = delegate;
    }

    @Override public void onFailure(okhttp3.Call call, IOException e) {
      delegate.onError(e);
    }

    /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
    @Override public void onResponse(okhttp3.Call call, Response response) {
      try {
        delegate.onSuccess(parseResponse(response, bodyConverter));
      } catch (Throwable e) {
        propagateIfFatal(e);
        delegate.onError(e);
      }
    }
  }

  static <V> V parseResponse(Response response, BodyConverter<V> bodyConverter) throws IOException {
    if (!HttpHeaders.hasBody(response)) {
      if (response.isSuccessful()) {
        return null;
      } else {
        throw new HttpException("response failed: " + response, response.code());
      }
    }
    try (ResponseBody responseBody = response.body()) {
      BufferedSource content = responseBody.source();
      if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) {
        content = Okio.buffer(new GzipSource(responseBody.source()));
      }
      if (response.isSuccessful()) {
        return bodyConverter.convert(content);
      } else {
        String tag = response.request().tag().toString();
        throw new HttpException("response for " + tag + " failed: " + content.readUtf8(),
          response.code());
      }
    }
  }
}
