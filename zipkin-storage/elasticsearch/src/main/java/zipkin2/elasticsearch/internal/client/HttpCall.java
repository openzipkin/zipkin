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
package zipkin2.elasticsearch.internal.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Semaphore;
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

public final class HttpCall<V> extends Call<V> {

  public interface BodyConverter<V> {
    V convert(BufferedSource content) throws IOException;
  }

  public static class Factory implements Closeable {
    final OkHttpClient ok;
    final Semaphore semaphore;
    public final HttpUrl baseUrl;

    public Factory(OkHttpClient ok, HttpUrl baseUrl) {
      this.ok = ok;
      this.semaphore = new Semaphore(ok.dispatcher().getMaxRequests());
      this.baseUrl = baseUrl;
    }

    public <V> HttpCall<V> newCall(Request request, BodyConverter<V> bodyConverter) {
      return new HttpCall<>(this, request, bodyConverter);
    }

    @Override public void close() {
      ok.dispatcher().executorService().shutdownNow();
    }
  }

  public final okhttp3.Call call;
  public final BodyConverter<V> bodyConverter;
  final Semaphore semaphore;


  HttpCall(Factory factory, Request request, BodyConverter<V> bodyConverter) {
    this(
      factory.ok.newCall(request),
      factory.semaphore,
      bodyConverter
    );
  }

  HttpCall(okhttp3.Call call, Semaphore semaphore, BodyConverter<V> bodyConverter) {
    this.call = call;
    this.semaphore = semaphore;
    this.bodyConverter = bodyConverter;
  }

  @Override public V execute() throws IOException {
    if (!semaphore.tryAcquire()) throw new IllegalStateException("over capacity");
    try {
      return parseResponse(call.execute(), bodyConverter);
    } finally {
      semaphore.release();
    }
  }

  @Override public void enqueue(Callback<V> delegate) {
    if (!semaphore.tryAcquire()) {
      delegate.onError(new IllegalStateException("over capacity"));
      return;
    }
    call.enqueue(new V2CallbackAdapter<>(semaphore, bodyConverter, delegate));
  }

  @Override public void cancel() {
    call.cancel();
  }

  @Override public boolean isCanceled() {
    return call.isCanceled();
  }

  @Override public HttpCall<V> clone() {
    return new HttpCall<V>(call.clone(), semaphore, bodyConverter);
  }

  static class V2CallbackAdapter<V> implements okhttp3.Callback {
    final Semaphore semaphore;
    final BodyConverter<V> bodyConverter;
    final Callback<V> delegate;

    V2CallbackAdapter(Semaphore semaphore, BodyConverter<V> bodyConverter, Callback<V> delegate) {
      this.semaphore = semaphore;
      this.bodyConverter = bodyConverter;
      this.delegate = delegate;
    }

    @Override public void onFailure(okhttp3.Call call, IOException e) {
      semaphore.release();
      delegate.onError(e);
    }

    /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
    @Override public void onResponse(okhttp3.Call call, Response response) {
      semaphore.release();
      try {
        delegate.onSuccess(parseResponse(response, bodyConverter));
      } catch (Throwable e) {
        propagateIfFatal(e);
        delegate.onError(e);
      }
    }
  }

  public static <V> V parseResponse(Response response, BodyConverter<V> bodyConverter)
    throws IOException {
    if (!HttpHeaders.hasBody(response)) {
      if (response.isSuccessful()) {
        return null;
      } else {
        throw new IllegalStateException("response failed: " + response);
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
        throw new IllegalStateException(
          "response for " + response.request().tag() + " failed: " + content.readUtf8());
      }
    }
  }
}
