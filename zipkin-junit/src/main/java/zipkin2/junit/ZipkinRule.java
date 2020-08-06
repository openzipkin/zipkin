/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.junit;

import com.linecorp.armeria.client.encoding.StreamDecoderFactory;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.annotation.Path;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.rules.ExternalResource;
import zipkin2.Callback;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.internal.Nullable;
import zipkin2.internal.Platform;
import zipkin2.storage.InMemoryStorage;

/**
 * Starts up a local Zipkin server, listening for http requests on {@link #httpUrl}.
 *
 * <p>This can be used to test instrumentation. For example, you can POST spans directly to this
 * server.
 *
 * <p>See http://openzipkin.github.io/zipkin-api/#/
 */
public final class ZipkinRule extends ExternalResource {
  private final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  private final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  private final Collector consumer =
    Collector.newBuilder(getClass()).storage(storage).metrics(metrics).build();
  private final BlockingQueue<AggregatedHttpResponse> failureQueue = new LinkedBlockingQueue<>();
  private final AtomicInteger receivedSpanBytes = new AtomicInteger();
  private final AtomicInteger requestCount = new AtomicInteger();

  private final ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) throws Exception {
      configureServer(sb);
    }
  };

  private Server manuallyStartedServer;

  /** Use this to connect. The zipkin v1 interface will be under "/api/v1" */
  public String httpUrl() {
    if (manuallyStartedServer != null) {
      return "http://localhost:" + manuallyStartedServer.activeLocalPort();
    }
    return server.httpUri().toString();
  }

  /** Use this to see how many requests you've sent to any zipkin http endpoint. */
  public int httpRequestCount() {
    return requestCount.get();
  }

  /** Use this to see how many spans or serialized bytes were collected on the http endpoint. */
  public InMemoryCollectorMetrics collectorMetrics() {
    return metrics;
  }

  /**
   * Stores the given spans directly, to setup preconditions for a test.
   *
   * <p>For example, if you are testing what happens when instrumentation adds a child to a trace,
   * you'd add the parent here.
   */
  public ZipkinRule storeSpans(List<Span> spans) {
    try {
      storage.accept(spans).execute();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
    return this;
  }

  /**
   * Adds a one-time failure to the http endpoint.
   *
   * <p>Ex. If you want to test that you don't repeatedly send bad data, you could send a 400 back.
   *
   * <pre>{@code
   * zipkin.enqueueFailure(sendErrorResponse(400, "bad format"));
   * }</pre>
   *
   * @param failure type of failure the next call to the http endpoint responds with
   */
  public ZipkinRule enqueueFailure(HttpFailure failure) {
    failureQueue.add(failure.response);
    return this;
  }

  /** Retrieves all traces this zipkin server has received. */
  public List<List<Span>> getTraces() {
    return storage.spanStore().getTraces();
  }

  /** Retrieves a trace by ID which Zipkin server has received, or null if not present. */
  @Nullable
  public List<Span> getTrace(String traceId) {
    List<Span> result;
    try {
      result = storage.traces().getTrace(traceId).execute();
    } catch (IOException e) {
      throw Platform.get().assertionError("I/O exception in in-memory storage", e);
    }
    // Note: this is a different behavior than Traces.getTrace() which is not nullable!
    return result.isEmpty() ? null : result;
  }

  /** Retrieves all service links between traces this zipkin server has received. */
  public List<DependencyLink> getDependencies() {
    return storage.spanStore().getDependencies();
  }

  /**
   * Used to manually start the server.
   *
   * @param httpPort choose 0 to select an available port
   */
  public void start(int httpPort) throws IOException {
    ServerBuilder sb = Server.builder()
      .http(httpPort);
    configureServer(sb);
    manuallyStartedServer = sb.build();
    manuallyStartedServer.start().join();
  }

  /** Used to manually stop the server. */
  public void shutdown() throws IOException {
    if (manuallyStartedServer != null) {
      manuallyStartedServer.stop();
    }
  }

  private void configureServer(ServerBuilder sb) {
    sb.annotatedService(new Object() {
      @Post
      @Path("/api/v1/spans")
      public HttpResponse v1(AggregatedHttpRequest req) {
        String type = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        SpanBytesDecoder decoder =
          type != null && type.contains("/x-thrift")
            ? SpanBytesDecoder.THRIFT
            : SpanBytesDecoder.JSON_V1;
        return acceptSpans(req, decoder);
      }

      @Post
      @Path("/api/v2/spans")
      public HttpResponse v2(AggregatedHttpRequest req) {
        String type = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        SpanBytesDecoder decoder =
          type != null && type.contains("/x-protobuf")
            ? SpanBytesDecoder.PROTO3
            : SpanBytesDecoder.JSON_V2;
        return acceptSpans(req, decoder);
      }
    });
    sb.decorator(((delegate, ctx, req) -> {
      requestCount.incrementAndGet();

      AggregatedHttpResponse maybeFailure = failureQueue.poll();
      if (maybeFailure != null) {
        if (maybeFailure == HttpFailure.DISCONNECT_DURING_REQUEST_BODY) {
          return HttpResponse.ofFailure(new IllegalStateException("Closed"));
        } else {
          return maybeFailure.toHttpResponse();
        }
      }
      HttpResponse response = delegate.serve(ctx, req);
      if (req.method() == HttpMethod.POST) {
        ctx.log()
          .whenComplete()
          .thenAccept(log -> receivedSpanBytes.addAndGet((int) log.requestLength()));
      }
      return response;
    }));
  }

  private HttpResponse acceptSpans(AggregatedHttpRequest request, SpanBytesDecoder decoder) {
    metrics.incrementMessages();
    String encoding = request.headers().get(HttpHeaderNames.CONTENT_ENCODING);
    HttpData content = request.content();

    if (content.isEmpty()) return HttpResponse.of(HttpStatus.ACCEPTED); // lenient on empty

    if (encoding != null && encoding.contains("gzip")) {
      content =
        StreamDecoderFactory.gzip().newDecoder(UnpooledByteBufAllocator.DEFAULT).decode(content);
      // The implementation of the armeria decoder is to return an empty body on failure
      if (content.isEmpty()) {
        metrics.incrementMessagesDropped();
        return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
          "Cannot gunzip spans");
      }
    }

    metrics.incrementBytes(content.length());

    CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
    consumer.acceptSpans(content.array(), decoder, new Callback<Void>() {
      @Override public void onSuccess(Void value) {
        responseFuture.complete(HttpResponse.of(HttpStatus.ACCEPTED));
      }

      @Override public void onError(Throwable t) {
        String message = t.getMessage();
        HttpStatus status = message.startsWith("Cannot store") ? HttpStatus.INTERNAL_SERVER_ERROR
          : HttpStatus.BAD_REQUEST;
        responseFuture.complete(HttpResponse.of(status, MediaType.PLAIN_TEXT_UTF_8, message));
      }
    });
    return HttpResponse.from(responseFuture);
  }

  @Override protected void before() {
    server.start();
  }

  @Override protected void after() {
    server.stop();
  }
}
