/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatusClass;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import zipkin2.Call;
import zipkin2.Callback;

public final class HttpCall<V> extends Call.Base<V> {

  public interface BodyConverter<V> {
    V convert(ByteBuffer content) throws IOException;
  }

  public static class Factory {
    final HttpClient httpClient;
    final Semaphore semaphore;
    public final String baseUrl;

    public Factory(HttpClient httpClient, String baseUrl, int maxRequests) {
      this.httpClient = httpClient;
      this.semaphore = new Semaphore(maxRequests);
      this.baseUrl = baseUrl;
    }

    public <V> HttpCall<V> newCall(AggregatedHttpRequest request, BodyConverter<V> bodyConverter) {
      return new HttpCall<>(this, request, bodyConverter);
    }
  }


  public final AggregatedHttpRequest request;
  public final BodyConverter<V> bodyConverter;

  final HttpClient httpClient;
  final Semaphore semaphore;

  volatile CompletableFuture<AggregatedHttpResponse> responseFuture;

  HttpCall(Factory factory, AggregatedHttpRequest request, BodyConverter<V> bodyConverter) {
    this(
      factory.httpClient,
      request,
      factory.semaphore,
      bodyConverter
    );
  }

  HttpCall(
    HttpClient httpClient, AggregatedHttpRequest request, Semaphore semaphore,
    BodyConverter<V> bodyConverter) {
    this.httpClient = httpClient;
    this.request = request;
    this.semaphore = semaphore;
    this.bodyConverter = bodyConverter;
  }

  @Override protected V doExecute() throws IOException {
    if (!semaphore.tryAcquire()) throw new IllegalStateException("over capacity");
    final AggregatedHttpResponse response;
    try {
      response = sendRequest().join();
    } finally {
      semaphore.release();
    }
    return parseResponse(response, bodyConverter);
  }

  @Override protected void doEnqueue(Callback<V> callback) {
    if (!semaphore.tryAcquire()) {
      callback.onError(new IllegalStateException("over capacity"));
      return;
    }
    sendRequest().handle((response, t) -> {
      semaphore.release();
      if (t != null) {
        callback.onError(t);
      } else {
        try {
          callback.onSuccess(parseResponse(response, bodyConverter));
        } catch (IOException e) {
          callback.onError(e);
        }
      }
      return null;
    });
  }

  @Override protected void doCancel() {
    CompletableFuture<AggregatedHttpResponse> responseFuture = this.responseFuture;
    if (responseFuture != null) {
      responseFuture.cancel(false);
    }
  }

  @Override public HttpCall<V> clone() {
    return new HttpCall<V>(httpClient, request, semaphore, bodyConverter);
  }

  @Override
  public String toString() {
    return "HttpCall(" + request + ")";
  }

  CompletableFuture<AggregatedHttpResponse> sendRequest() {
    CompletableFuture<AggregatedHttpResponse> responseFuture =
      httpClient.execute(request).aggregate();
    this.responseFuture = responseFuture;
    return responseFuture;
  }

  <V> V parseResponse(AggregatedHttpResponse response, BodyConverter<V> bodyConverter)
    throws IOException {
    if (response.content().isEmpty()) {
      if (response.status().codeClass().equals(HttpStatusClass.SUCCESS)) {
        return null;
      } else {
        throw new IllegalStateException("response failed: " + response);
      }
    }

    HttpData content = response.content();
    try {
      if (response.status().codeClass().equals(HttpStatusClass.SUCCESS)) {
        final ByteBuffer buf;
        if (content instanceof ByteBufHolder) {
          buf = ((ByteBufHolder) content).content().nioBuffer();
        } else {
          buf = ByteBuffer.wrap(content.array());
        }
        return bodyConverter.convert(buf);
      } else {
        throw new IllegalStateException(
          "response for " + request.path() + " failed: " + response.contentUtf8());
      }
    } finally {
      ReferenceCountUtil.safeRelease(content);
    }
  }
}
