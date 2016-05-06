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
package zipkin.server.brave;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.LatencyTracker;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.reflect.Proxy.getInvocationHandler;
import static java.util.Collections.singletonMap;
import static zipkin.internal.Util.checkNotNull;

/**
 * Creates traced sessions, which write directly to brave's collector to preserve the correct
 * duration.
 */
public final class TracedSession implements InvocationHandler, LatencyTracker {

  private final ProtocolVersion version;

  public static Session create(Session target, Brave brave, SpanCollector collector) {
    TracedSession traced = new TracedSession(target, brave, collector);
    return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
        new Class<?>[] {Session.class}, traced);
  }

  final Session target;
  final Brave brave;
  final SpanCollector collector;
  /**
   * Manual Propagation, as opposed to attempting to control the {@link
   * Cluster#register(LatencyTracker) latency tracker callback thread}.
   */
  final Map<BoundStatement, Span> cache = new LinkedHashMap<>();

  TracedSession(Session target, Brave brave, SpanCollector collector) {
    this.target = checkNotNull(target, "target");
    this.brave = checkNotNull(brave, "brave");
    this.collector = checkNotNull(collector, "collector");
    this.version = target.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
    target.getCluster().register(this);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    switch (method.getName()) {
      case "equals": // allow proxies to be equivalent
        try {
          Object that = args.length > 0 && args[0] != null ? getInvocationHandler(args[0]) : null;
          return equals(that);
        } catch (IllegalArgumentException e) {
          return false;
        }
      case "hashCode":
        return hashCode();
      case "toString":
        return toString();
    }

    /** Only trace bound statements for now, since that's what we use */
    if (method.getName().equals("executeAsync") && args[0] instanceof BoundStatement) {
      BoundStatement statement = (BoundStatement) args[0];
      SpanId spanId = brave.clientTracer().startNewSpan("bound-statement");
      // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
      if (spanId != null && spanId.nullableParentId() == null) {
        brave.clientSpanThreadBinder().setCurrentSpan(null);
        spanId = null;
      }
      if (spanId == null) return method.invoke(target, args);

      // o.a.c.tracing.Tracing.newSession must use the same format for the key zipkin
      if (version.compareTo(ProtocolVersion.V4) >= 0) {
        statement.enableTracing();
        statement.setOutgoingPayload(singletonMap("zipkin", ByteBuffer.wrap(spanId.bytes())));
      }

      brave.clientTracer().setClientSent(); // start the span and store it
      brave.clientTracer()
          .submitBinaryAnnotation("cql.query", statement.preparedStatement().getQueryString());
      synchronized (cache) {
        cache.put(statement, brave.clientSpanThreadBinder().getCurrentClientSpan());
      }
      // let go of the client span as it is only used for the RPC (will have no local children)
      brave.clientSpanThreadBinder().setCurrentSpan(null);
      return target.executeAsync(statement);
    }
    return method.invoke(target, args);
  }

  @Override public void update(Host host, Statement statement, Exception e, long nanos) {
    Span span = null;
    synchronized (cache) {
      span = cache.remove(statement);
    }
    if (span == null) {
      checkState(!statement.isTracing(), "%s not in the cache eventhough tracing is on", statement);
      return;
    }
    span.setDuration(nanos / 1000); // TODO: allow client tracer to end with duration
    Endpoint local = span.getAnnotations().get(0).host; // TODO: expose in brave
    long endTs = span.getTimestamp() + span.getDuration();
    if (e != null) {
      span.addToBinary_annotations(BinaryAnnotation.create("cql.error", e.getMessage(), local));
    } else {
      span.addToAnnotations(Annotation.create(endTs, "cr", local));
    }
    int ipv4 = ByteBuffer.wrap(host.getAddress().getAddress()).getInt();
    Endpoint endpoint = Endpoint.create("cassandra", ipv4, host.getSocketAddress().getPort());
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
}
