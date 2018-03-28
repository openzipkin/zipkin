/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.cassandra3;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.datastax.driver.core.AbstractSession;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.google.common.base.CaseFormat;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import zipkin2.Endpoint;

/**
 * Differs from https://github.com/openzipkin/brave-cassandra in the following ways:
 * <pre><ul>
 *   <li>Doesn't propagate (due to cassandra 3.11.1 java.nio.BufferUnderflowException: null)</li>
 *   <li>Doesn't trace unless there's a current span (to prevent write amplification)</li>
 *   <li>Hard codes tagging policy (to keep the size of the file small</li>
 * </ul></pre>
 */
final class TracingSession extends AbstractSession {

  final Tracer tracer;
  final String remoteServiceName;
  final String keyspace;
  final Session delegate;

  TracingSession(Tracing tracing, Session target) {
    delegate = target;
    tracer = tracing.tracer();
    remoteServiceName = target.getCluster().getClusterName();
    keyspace = delegate.getLoggedKeyspace();
  }

  @Override public ResultSetFuture executeAsync(Statement statement) {
    // don't start new traces (to prevent amplifying writes to local storage)
    if (tracer.currentSpan() == null) {
      return delegate.executeAsync(statement);
    }

    Span span = tracer.nextSpan();
    if (!span.isNoop()) {
      span.name(spanName(statement));
      span.tag("cassandra.keyspace", keyspace);
      if (statement instanceof BoundStatement) {
        BoundStatement boundStatement = (BoundStatement) statement;
        span.tag("cassandra.query", boundStatement.preparedStatement().getQueryString());
      }
    }

    span.start();
    ResultSetFuture result;
    try {
      result = delegate.executeAsync(statement);
    } catch (RuntimeException | Error e) {
      if (span.isNoop()) throw e;
      addError(e, span);
      span.finish();
      throw e;
    }
    if (span.isNoop()) return result; // don't add callback on noop
    Futures.addCallback(result, new FutureCallback<ResultSet>() {
      @Override public void onSuccess(ResultSet result) {
        InetSocketAddress host = result.getExecutionInfo().getQueriedHost().getSocketAddress();
        span.remoteEndpoint(Endpoint.newBuilder()
          .serviceName(remoteServiceName)
          .ip(host.getAddress())
          .port(host.getPort())
          .build()
        );
        span.finish();
      }

      @Override public void onFailure(Throwable e) {
        addError(e, span);
        span.finish();
      }
    });
    return result;
  }

  @Override protected ListenableFuture<PreparedStatement> prepareAsync(String query,
    Map<String, ByteBuffer> customPayload) {
    SimpleStatement statement = new SimpleStatement(query);
    statement.setOutgoingPayload(customPayload);
    return prepareAsync(statement);
  }

  @Override public ListenableFuture<PreparedStatement> prepareAsync(String query) {
    return delegate.prepareAsync(query);
  }

  @Override public String getLoggedKeyspace() {
    return delegate.getLoggedKeyspace();
  }

  @Override public Session init() {
    return delegate.init();
  }

  @Override public ListenableFuture<Session> initAsync() {
    return delegate.initAsync();
  }

  @Override public ListenableFuture<PreparedStatement> prepareAsync(RegularStatement statement) {
    return delegate.prepareAsync(statement);
  }

  @Override public CloseFuture closeAsync() {
    return delegate.closeAsync();
  }

  @Override public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override public Cluster getCluster() {
    return delegate.getCluster();
  }

  @Override public State getState() {
    return delegate.getState();
  }

  static void addError(Throwable e, Span span) {
    String message = e.getMessage();
    span.tag("error", message != null ? message : e.getClass().getSimpleName());
  }

  /** Returns the span name of the statement. Defaults to the lower-camel case type name. */
  static String spanName(Statement statement) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, statement.getClass().getSimpleName());
  }
}
