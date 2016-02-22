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
package zipkin.junit;

import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import zipkin.InMemorySpanStore;
import zipkin.Span;

/**
 * Starts up a local Zipkin server, listening for http requests on {@link #httpUrl}.
 *
 * <p>This can be used to test instrumentation. For example, you can POST spans directly to this
 * server.
 *
 * See http://openzipkin.github.io/zipkin-api/#/
 */
public final class ZipkinRule implements TestRule {
  private final InMemorySpanStore store = new InMemorySpanStore();
  private final MockWebServer server = new MockWebServer();

  public ZipkinRule() {
    server.setDispatcher(new ZipkinDispatcher(store, server));
  }

  /** Use this to connect. The zipkin v1 interface will be under "/api/v1" */
  public String httpUrl() {
    return String.format("http://%s:%s", server.getHostName(), server.getPort());
  }

  /**
   * Stores the given spans directly, to setup preconditions for a test.
   *
   * <p>For example, if you are testing what happens when instrumentation adds a child to a trace,
   * you'd add the parent here.
   */
  public ZipkinRule storeSpans(Iterable<Span> spans) {
    store.accept(spans.iterator());
    return this;
  }

  /** Retrieves all traces this zipkin server has received. */
  public List<List<Span>> getTraces() {
    return store.getTracesByIds(store.traceIds());
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
