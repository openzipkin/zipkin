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

import com.datastax.driver.core.AbstractSession;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.twitter.zipkin.gen.Span;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import zipkin.storage.cassandra3.Cassandra3Storage;

final class TracedSession extends AbstractSession {

    private static final Executor EXECUTOR = MoreExecutors.directExecutor();
    private final Session session;
    private final Brave brave;
    private final SpanCollector collector;
    private final ClientTracer clientTracer;
    private final long useKeyspaceTimeout;
    private volatile boolean keyspaceSet = false;
    private final ProtocolVersion version;

    static TracedSession create(final Session session, Brave brave, SpanCollector collector) {
        return new TracedSession(session, brave, collector);
    }

    private TracedSession(final Session session, Brave brave, SpanCollector collector) {
        this.session = session;
        this.brave = brave;
        this.collector = collector;
        this.clientTracer = brave.clientTracer();
        this.useKeyspaceTimeout = session.getCluster().getConfiguration().getSocketOptions().getConnectTimeoutMillis();
        this.version = session.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
    }

    @Override
    public String getLoggedKeyspace() {
        return session.getLoggedKeyspace();
    }

    @Override
    public Session init() {
        return session.init();
    }

    @Override
    public ResultSetFuture executeAsync(final Statement statement) {
        // don't trace any already self-traced spans
        boolean alreadySelfTraced = null != statement.getOutgoingPayload()
                && statement.getOutgoingPayload().containsKey(Cassandra3Storage.SELF_TRACING_KEY);

        // o.a.c.tracing.Tracing.newSession must use the same format for the key zipkin
        boolean supported = version.compareTo(ProtocolVersion.V4) >= 0;
        if (supported && !alreadySelfTraced) {
            useKeyspace(session, statement.getKeyspace());
            SpanId spanId = null;
            spanId = clientTracer.startNewSpan(statement.toString());
            clientTracer.submitBinaryAnnotation(Cassandra3Storage.SELF_TRACING_KEY, "true");
            if (null != spanId) {
                String ks = null != statement.getKeyspace() ? statement.getKeyspace() : session.getLoggedKeyspace();
                assert null != ks : "this cluster/session only for keyspace " + ks;
                clientTracer.setClientSent();
            }
            statement.enableTracing();
            Map<String,ByteBuffer> payload = new HashMap<>();
            if (null != statement.getOutgoingPayload()) {
                payload.putAll(statement.getOutgoingPayload());
            }
            payload.put("zipkin", ByteBuffer.wrap(spanId.bytes()));
            statement.setOutgoingPayload(payload);

            final Span span = null != spanId ? brave.clientSpanThreadBinder().getCurrentClientSpan() : null;
            ResultSetFuture future = session.executeAsync(statement);
            if (null != spanId) {
                future.addListener(new Runnable() {
                    @Override
                    public void run() {
                        brave.clientSpanThreadBinder().setCurrentSpan(span);
                        clientTracer.setClientReceived();
                        collector.collect(span);
                        brave.clientSpanThreadBinder().setCurrentSpan(null);
                    }
                }, EXECUTOR);
            }
            return future;
        }
        return session.executeAsync(statement);
    }

    @Override
    public ListenableFuture<PreparedStatement> prepareAsync(final String query) {
        useKeyspace(session, null);
        return session.prepareAsync(query);
    }

    @Override
    public ListenableFuture<PreparedStatement> prepareAsync(final RegularStatement statement) {
        useKeyspace(session, statement.getKeyspace());
        return super.prepareAsync(statement);
    }

    @Override
    public CloseFuture closeAsync() {
        return session.closeAsync();
    }

    @Override
    public boolean isClosed() {
        return session.isClosed();
    }

    @Override
    public Cluster getCluster() {
        return session.getCluster();
    }

    @Override
    public Session.State getState() {
        return session.getState();
    }

    private void useKeyspace(final Session session, final String ks) {
        String keyspace = null != ks ? ks : session.getLoggedKeyspace();
        // this method exists because ZipkinTracingCluster.connect() loops if it calls super.connect(keyspace)
        if (!keyspaceSet) {
            try {
                ResultSetFuture future = session.executeAsync("USE " + QueryBuilder.quote(keyspace));
                Uninterruptibles.getUninterruptibly(future, useKeyspaceTimeout, TimeUnit.MILLISECONDS);
                keyspaceSet = true;
            } catch (TimeoutException e) {
                throw new DriverInternalError(""
                        + "No responses after " + useKeyspaceTimeout + " milliseconds while setting current keyspace. "
                        + "This should not happen, unless you have setup a very low connection timeout.");
            } catch (ExecutionException e) {
                // see DefaultResultSetFuture.extractCauseFromExecutionException(e);
                if (e.getCause() instanceof DriverException) {
                    throw ((DriverException) e.getCause()).copy();
                } else {
                    throw new DriverInternalError("Unexpected exception thrown", e.getCause());
                }
            } catch (RuntimeException e) {
                session.close();
                throw e;
            }
        }
    }

    @Override
    protected ListenableFuture<PreparedStatement> prepareAsync(String query, Map<String, ByteBuffer> customPayload) {
        return session.prepareAsync(query);
    }

    @Override
    public ListenableFuture<Session> initAsync() {
        return session.initAsync();
    }

}
