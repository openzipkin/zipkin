/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.util.function.BiFunction;
import zipkin2.internal.ClosedComponentException;
import zipkin2.storage.cassandra.CassandraStorage.SessionFactory;

import static zipkin2.Call.propagateIfFatal;
import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

final class LazySession {
  final CassandraStorage storage;
  final SessionFactory sessionFactory;
  final BiFunction<CassandraStorage, CqlSession, Schema.Metadata> ensureSchema;

  volatile CqlSession session;
  volatile PreparedStatement healthCheck; // guarded by session
  volatile Schema.Metadata metadata; // guarded by session

  LazySession(CassandraStorage storage, SessionFactory sessionFactory,
    BiFunction<CassandraStorage, CqlSession, Schema.Metadata> ensureSchema) {
    this.sessionFactory = sessionFactory;
    this.ensureSchema = ensureSchema;
    this.storage = storage;
  }

  /** Creates a session and ensures schema if configured. */
  CqlSession get() {
    if (session != null) return session;
    synchronized (this) {
      if (session != null) return session; // lost race
      session = sessionFactory.create(storage);

      // If we got this far, the session is healthy. So, everything below only happens once.
      try {
        metadata = ensureSchema.apply(storage, session);
        session.execute("USE " + storage.keyspace);
        Schema.initializeUDTs(session, storage.keyspace);
        healthCheck = session.prepare("SELECT trace_id FROM " + TABLE_SPAN + " limit 1");
      } catch (RuntimeException | Error e) {
        propagateIfFatal(e);
        // An error here was from installing or validating the schema. To ensure we don't repeat
        // failed commands, close, but don't null the session. For example, repeating may look like
        // an upgrade due to the first failure, and distract from the original problem.
        session.close();
      }
    }
    if (session.isClosed()) {
      throw new ClosedComponentException("Session initialization failed. See server logs");
    }
    return session;
  }

  Schema.Metadata metadata() {
    get();
    return metadata;
  }

  void healthCheck() {
    get();
    session.execute(healthCheck.bind());
  }

  void close() {
    CqlSession maybeSession = session;
    if (maybeSession != null) {
      session.close();
      session = null;
    }
  }
}
