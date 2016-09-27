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
package zipkin.autoconfigure.storage.cassandra3.brave;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.LatencyTracker;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.google.common.collect.Maps;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Constants;

import static java.util.Collections.singletonMap;
import static zipkin.internal.Util.checkNotNull;

/**
 * Creates traced sessions, which write directly to brave's collector to preserve the correct
 * duration.
 */
public final class TracedSession extends AbstractInvocationHandler implements LatencyTracker {
  private static final Logger LOG = LoggerFactory.getLogger(TracedSession.class);

  private final ProtocolVersion version;

  public static Session create(Session target, Brave brave, SpanCollector collector) {
    return Reflection.newProxy(Session.class, new TracedSession(target, brave, collector));
  }

  final Session target;
  final Brave brave;
  final SpanCollector collector;
  /**
   * Manual Propagation, as opposed to attempting to control the {@link
   * Cluster#register(LatencyTracker) latency tracker callback thread}.
   */
  final Map<BoundStatement, Span> cache = Maps.newConcurrentMap();

  TracedSession(Session target, Brave brave, SpanCollector collector) {
    this.target = checkNotNull(target, "target");
    this.brave = checkNotNull(brave, "brave");
    this.collector = checkNotNull(collector, "collector");
    this.version = target.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
    target.getCluster().register(this);
  }

  @Override protected Object handleInvocation(Object proxy, Method method, Object[] args)
      throws Throwable {
    // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
    if (brave.serverSpanThreadBinder().getCurrentServerSpan() != null &&
        brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan() != null
        && method.getName().equals("executeAsync") && args[0] instanceof BoundStatement) {
      BoundStatement statement = (BoundStatement) args[0];

      // via an internal class z.s.cassandra3.NamedBoundStatement, toString() is a nice name
      SpanId spanId = brave.clientTracer().startNewSpan(statement.toString());

      // o.a.c.tracing.Tracing.newSession must use the same format for the key zipkin
      if (version.compareTo(ProtocolVersion.V4) >= 0) {
        statement.enableTracing();
        statement.setOutgoingPayload(singletonMap("zipkin", ByteBuffer.wrap(spanId.bytes())));
      }

      brave.clientTracer().setClientSent(); // start the span and store it
      brave.clientTracer()
          .submitBinaryAnnotation("cql.query", statement.preparedStatement().getQueryString());
      cache.put(statement, brave.clientSpanThreadBinder().getCurrentClientSpan());
      // let go of the client span as it is only used for the RPC (will have no local children)
      brave.clientSpanThreadBinder().setCurrentSpan(null);
      return new BraveResultSetFuture(target.executeAsync(statement), brave);
    }
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) throw e.getCause();
      throw e;
    }
  }

  @Override public void update(Host host, Statement statement, Exception e, long nanos) {
    if (!(statement instanceof BoundStatement)) return;
    Span span = cache.remove(statement);
    if (span == null) {
      if (statement.isTracing()) {
        LOG.warn("{} not in the cache eventhough tracing is on", statement);
      }
      return;
    }
    span.setDuration(nanos / 1000); // TODO: allow client tracer to end with duration
    Endpoint local = span.getAnnotations().get(0).host; // TODO: expose in brave
    long endTs = span.getTimestamp() + span.getDuration();
    span.addToAnnotations(Annotation.create(endTs, "cr", local));
    if (e != null) {
      span.addToBinary_annotations(BinaryAnnotation.create(Constants.ERROR, e.getMessage(), local));
    }
    int ipv4 = ByteBuffer.wrap(host.getAddress().getAddress()).getInt();
    Endpoint endpoint = Endpoint.create("cassandra3", ipv4, host.getSocketAddress().getPort());
    span.addToBinary_annotations(BinaryAnnotation.address("sa", endpoint));
    collector.collect(span);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TracedSession) {
      TracedSession other = (TracedSession) obj;
      return target.equals(other.target);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return target.hashCode();
  }

  @Override
  public String toString() {
    return target.toString();
  }

  @Override public void onRegister(Cluster cluster) {
  }

  @Override public void onUnregister(Cluster cluster) {
  }

  static class BraveResultSetFuture<T> implements ResultSetFuture {
    final ResultSetFuture delegate;
    final ServerSpanThreadBinder threadBinder;
    final ServerSpan parent;

    BraveResultSetFuture(ResultSetFuture delegate, Brave brave) {
      this.delegate = delegate;
      this.threadBinder = brave.serverSpanThreadBinder();
      this.parent = threadBinder.getCurrentServerSpan();
    }

    @Override public ResultSet getUninterruptibly() {
      return delegate.getUninterruptibly();
    }

    @Override public ResultSet getUninterruptibly(long timeout, TimeUnit unit)
        throws TimeoutException {
      return delegate.getUninterruptibly(timeout, unit);
    }

    @Override public boolean cancel(boolean mayInterruptIfRunning) {
      return delegate.cancel(mayInterruptIfRunning);
    }

    @Override public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override public boolean isDone() {
      return delegate.isDone();
    }

    @Override public ResultSet get() throws InterruptedException, ExecutionException {
      return delegate.get();
    }

    @Override public ResultSet get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return delegate.get(timeout, unit);
    }

    @Override public void addListener(Runnable listener, Executor executor) {
      delegate.addListener(() -> {
        threadBinder.setCurrentSpan(parent);
        try {
          listener.run();
        } finally {
          threadBinder.setCurrentSpan(null);
        }
      }, executor);
    }
  }
}
