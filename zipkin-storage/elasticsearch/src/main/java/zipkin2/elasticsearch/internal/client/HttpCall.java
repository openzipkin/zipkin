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
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatusClass;
import com.linecorp.armeria.common.RequestContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import okio.BufferedSource;
import okio.Okio;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.ReadBuffer;

public final class HttpCall<V> extends Call.Base<V> {

  public interface BodyConverter<V> {
    V convert(BufferedSource content) throws IOException;
  }

  public static class Factory {
    final HttpClient httpClient;

    public Factory(HttpClient httpClient) {
      this.httpClient = httpClient;
    }

    public <V> HttpCall<V> newCall(AggregatedHttpRequest request, BodyConverter<V> bodyConverter) {
      return new HttpCall<>(httpClient, request, bodyConverter);
    }
  }


  public final AggregatedHttpRequest request;
  public final BodyConverter<V> bodyConverter;

  final HttpClient httpClient;

  volatile CompletableFuture<AggregatedHttpResponse> responseFuture;

  HttpCall(HttpClient httpClient, AggregatedHttpRequest request, BodyConverter<V> bodyConverter) {
    this.httpClient = httpClient;

    if (request.content() instanceof ByteBufHolder) {
      // Unfortunately it's not possible to use pooled objects in requests and support clone() after
      // sending the request.
      ByteBuf buf = ((ByteBufHolder) request.content()).content();
      try {
        this.request = AggregatedHttpRequest.of(
          request.headers(), HttpData.copyOf(buf), request.trailers());
      } finally {
        buf.release();
      }
    } else {
      this.request = request;
    }

    this.bodyConverter = bodyConverter;
  }

  @Override protected V doExecute() throws IOException {
    AggregatedHttpResponse response = sendRequest().join();
    return parseResponse(response, bodyConverter);
  }

  @Override protected void doEnqueue(Callback<V> callback) {
    sendRequest().handle((response, t) -> {
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
    return new HttpCall<>(httpClient, request, bodyConverter);
  }

  @Override
  public String toString() {
    return "HttpCall(" + request + ")";
  }

  CompletableFuture<AggregatedHttpResponse> sendRequest() {
    HttpResponse response = httpClient.execute(request);
    CompletableFuture<AggregatedHttpResponse> responseFuture =
      RequestContext.mapCurrent(
        ctx -> response.aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc()),
        // This should never be used in practice since the module runs in an Armeria server.
        response::aggregate);
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
        return bodyConverter.convert(Okio.buffer(Okio.source(ReadBuffer.wrapUnsafe(buf))));
      } else {
        throw new IllegalStateException(
          "response for " + request.path() + " failed: " + response.contentUtf8());
      }
    } finally {
      ReferenceCountUtil.safeRelease(content);
    }
  }
}
