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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.Session;

final class LazySession {
  private final SessionFactory sessionFactory;
  private final CassandraStorage storage;
  private volatile Session session;
  private volatile Schema.Metadata metadata; // guarded by session

  LazySession(SessionFactory sessionFactory, CassandraStorage storage) {
    this.sessionFactory = sessionFactory;
    this.storage = storage;
  }

  Session get() {
    if (session == null) {
      synchronized (this) {
        if (session == null) {
          session = sessionFactory.create(storage);
          metadata = Schema.readMetadata(session); // warn only once when schema problems exist
        }
      }
    }
    return session;
  }

  Schema.Metadata metadata() {
    get();
    return metadata;
  }

  void close() {
    Session maybeSession = session;
    if (maybeSession != null) maybeSession.close();
  }
}
