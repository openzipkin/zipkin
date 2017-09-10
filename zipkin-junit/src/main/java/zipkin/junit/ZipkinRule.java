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
package zipkin.junit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.GzipSink;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import zipkin.Span;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.V2SpanConverter;
import zipkin2.internal.Platform;
import zipkin2.storage.InMemoryStorage;

import static okhttp3.mockwebserver.SocketPolicy.KEEP_OPEN;
import static zipkin.internal.GroupByTraceId.TRACE_DESCENDING;

/**
 * Starts up a local Zipkin server, listening for http requests on {@link #httpUrl}.
 *
 * <p>This can be used to test instrumentation. For example, you can POST spans directly to this
 * server.
 *
 * See http://openzipkin.github.io/zipkin-api/#/
 */
public final class ZipkinRule implements TestRule {
  private final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  private final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  private final MockWebServer server = new MockWebServer();
  private final BlockingQueue<MockResponse> failureQueue = new LinkedBlockingQueue<>();
  private final AtomicInteger receivedSpanBytes = new AtomicInteger();

  public ZipkinRule() {
    Dispatcher dispatcher = new Dispatcher() {
      final ZipkinDispatcher successDispatch = new ZipkinDispatcher(storage, metrics, server);

      @Override
      public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
        MockResponse maybeFailure = failureQueue.poll();
        if (maybeFailure != null) return maybeFailure;
        MockResponse result = successDispatch.dispatch(request);
        if (request.getMethod().equals("POST")) {
          receivedSpanBytes.addAndGet((int) request.getBodySize());
        }
        String encoding = request.getHeaders().get("Accept-Encoding");
        if (result.getBody() != null && encoding != null && encoding.contains("gzip")) {
          try {
            Buffer sink = new Buffer();
            GzipSink gzipSink = new GzipSink(sink);
            gzipSink.write(result.getBody(), result.getBody().size());
            gzipSink.close();
            result.setBody(sink);
          } catch (IOException e) {
            throw new AssertionError(e);
          }
          result.setHeader("Content-Encoding", "gzip");
        }
        return result;
      }

      @Override
      public MockResponse peek() {
        MockResponse maybeFailure = failureQueue.peek();
        if (maybeFailure != null) return maybeFailure;
        return new MockResponse().setSocketPolicy(KEEP_OPEN);
      }
    };
    server.setDispatcher(dispatcher);
  }

  /** Use this to connect. The zipkin v1 interface will be under "/api/v1" */
  public String httpUrl() {
    return String.format("http://%s:%s", server.getHostName(), server.getPort());
  }

  /** Use this to see how many requests you've sent to any zipkin http endpoint. */
  public int httpRequestCount() {
    return server.getRequestCount();
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
      storage.accept(V2SpanConverter.fromSpans(spans)).execute();
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
    List<List<zipkin2.Span>> traces = storage.spanStore().getTraces();
    List<List<Span>> result = new ArrayList<>(traces.size());
    for (List<zipkin2.Span> trace2 : traces) {
      List<Span> sameTraceId = new ArrayList<>();
      for (zipkin2.Span span2 : trace2) {
        sameTraceId.add(V2SpanConverter.toSpan(span2));
      }
      result.addAll(GroupByTraceId.apply(sameTraceId, false, false));
    }
    Collections.sort(result, TRACE_DESCENDING);
    return result;
  }

  /**
   * Used to manually start the server.
   *
   * @param httpPort choose 0 to select an available port
   */
  public void start(int httpPort) throws IOException {
    server.start(httpPort);
  }

  /** Used to manually stop the server. */
  public void shutdown() throws IOException {
    server.shutdown();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return server.apply(base, description);
  }
}
