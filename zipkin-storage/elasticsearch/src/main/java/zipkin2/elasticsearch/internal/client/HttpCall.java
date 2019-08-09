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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.UnprocessedRequestException;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.HttpStatusClass;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.util.Exceptions;
import com.linecorp.armeria.common.util.SafeCloseable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Callback;

import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;
import static zipkin2.elasticsearch.internal.JsonSerializers.OBJECT_MAPPER;

public final class HttpCall<V> extends Call.Base<V> {
  public static final AttributeKey<String> NAME = AttributeKey.valueOf("name");

  public interface BodyConverter<V> {
    /**
     * Prefer using the {@code parser} for request-scoped conversions. Typically, {@code
     * contentString} is only for an unexpected failure.
     */
    V convert(JsonParser parser, Supplier<String> contentString) throws IOException;
  }

  public static class Factory {
    final HttpClient httpClient;

    public Factory(HttpClient httpClient) {
      this.httpClient = httpClient;
    }

    public <V> HttpCall<V> newCall(
      AggregatedHttpRequest request, BodyConverter<V> bodyConverter, String name) {
      return new HttpCall<>(httpClient, request, bodyConverter, name);
    }
  }

  // Visible for benchmarks
  public final AggregatedHttpRequest request;
  final BodyConverter<V> bodyConverter;
  final String name;

  final HttpClient httpClient;

  volatile CompletableFuture<AggregatedHttpResponse> responseFuture;

  HttpCall(HttpClient httpClient, AggregatedHttpRequest request, BodyConverter<V> bodyConverter,
    String name) {
    this.httpClient = httpClient;
    this.name = name;

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
    // TODO: testme
    for (EventExecutor eventLoop : httpClient.factory().eventLoopGroup()) {
      if (eventLoop.inEventLoop()) {
        throw new RuntimeException("Attempting to make a blocking request from an event loop. "
          + "Either use doEnqueue() or run this in a separate thread.");
      }
    }
    final AggregatedHttpResponse response;
    try {
      response = sendRequest().join();
    } catch (CompletionException e) {
      propagateIfFatal(e);
      Exceptions.throwUnsafely(e.getCause());
      return null;  // Unreachable
    }
    return parseResponse(response, bodyConverter);
  }

  @Override protected void doEnqueue(Callback<V> callback) {
    sendRequest().handle((response, t) -> {
      if (t != null) {
        callback.onError(t);
      } else {
        try {
          V value = parseResponse(response, bodyConverter);
          callback.onSuccess(value);
        } catch (Throwable t1) {
          propagateIfFatal(t1);
          callback.onError(t1);
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
    return new HttpCall<>(httpClient, request, bodyConverter, name);
  }

  @Override public String toString() {
    return "HttpCall(" + request + ")";
  }

  CompletableFuture<AggregatedHttpResponse> sendRequest() {
    final HttpResponse response;
    try (SafeCloseable ignored = Clients.withContextCustomizer(ctx -> ctx.attr(NAME).set(name))) {
      response = httpClient.execute(request);
    }
    CompletableFuture<AggregatedHttpResponse> responseFuture =
      RequestContext.mapCurrent(
        ctx -> response.aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc()),
        // This should never be used in practice since the module runs in an Armeria server.
        response::aggregate);
    responseFuture = responseFuture.exceptionally(t -> {
      if (t instanceof UnprocessedRequestException) {
        Throwable cause = t.getCause();
        // Go ahead and reduce the output in logs since this is usually a configuration or
        // infrastructure issue and the Armeria stack trace won't help debugging that.
        Exceptions.clearTrace(cause);
        throw new RejectedExecutionException("Rejected execution: " + cause.getMessage(), cause);
      } else {
        Exceptions.throwUnsafely(t);
      }
      return null;
    });
    this.responseFuture = responseFuture;
    return responseFuture;
  }

  <V> V parseResponse(AggregatedHttpResponse response, BodyConverter<V> bodyConverter)
    throws IOException {
    // Handle the case where there is no content, as that means we have no resources to release.
    HttpStatus status = response.status();
    if (response.content().isEmpty()) {
      if (status.codeClass().equals(HttpStatusClass.SUCCESS)) {
        return null;
      } else if (status.code() == 404) {
        throw new FileNotFoundException(request.path());
      } else {
        throw new RuntimeException(
          "response for " + request.path() + " failed: " + response.status());
      }
    }

    // If this is a client or server error, we look for a json message.
    if ((status.codeClass().equals(HttpStatusClass.CLIENT_ERROR)
      || status.codeClass().equals(HttpStatusClass.SERVER_ERROR))) {
      bodyConverter = (parser, contentString) -> {
        String message = null;
        try {
          JsonNode root = OBJECT_MAPPER.readTree(parser);
          message = root.findPath("reason").textValue();
          if (message == null) message = root.at("/Message").textValue();
        } catch (RuntimeException | IOException possiblyParseException) {
        }
        throw new RuntimeException(message != null ? message
          : "response for " + request.path() + " failed: " + contentString.get());
      };
    }

    HttpData content = response.content();
    try (InputStream stream = content.toInputStream();
         JsonParser parser = JSON_FACTORY.createParser(stream)) {

      if (status.code() == 404) throw new FileNotFoundException(request.path());

      return bodyConverter.convert(parser, content::toStringUtf8);
    } finally {
      // toInputStream creates an additional reference instead of itself releasing content()
      ReferenceCountUtil.safeRelease(content);
      ReferenceCountUtil.safeRelease(content);
    }
  }
}
