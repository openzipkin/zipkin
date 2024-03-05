/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import zipkin2.storage.cassandra.CassandraStorage.SessionFactory;

import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

final class LazySession {
  final SessionFactory sessionFactory;
  final CassandraStorage storage;
  volatile CqlSession session;
  volatile PreparedStatement healthCheck; // guarded by session
  volatile Schema.Metadata metadata; // guarded by session

  LazySession(SessionFactory sessionFactory, CassandraStorage storage) {
    this.sessionFactory = sessionFactory;
    this.storage = storage;
  }

  CqlSession get() {
    if (session == null) {
      synchronized (this) {
        if (session == null) {
          session = sessionFactory.create(storage);
          // cached here to warn only once when schema problems exist
          metadata = Schema.readMetadata(session, storage.keyspace);
          healthCheck = session.prepare("SELECT trace_id FROM " + TABLE_SPAN + " limit 1");
        }
      }
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
