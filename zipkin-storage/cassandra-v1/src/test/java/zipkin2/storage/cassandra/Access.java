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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.Session;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** opens package access for testing */
public final class Access {

  // Builds a session without trying to use a namespace or init UDTs
  public static Session tryToInitializeSession(String contactPoint) {
    CassandraStorage storage = CassandraStorage.newBuilder()
      .contactPoints(contactPoint)
      .maxConnections(1)
      .keyspace("test_cassandra_v1").build();
    Session session = null;
    try {
      session = DefaultSessionFactory.buildSession(storage);
      session.execute("SELECT now() FROM system.local");
    } catch (Throwable e) {
      if (session != null) session.getCluster().close();
      assumeTrue(false, e.getMessage());
    }
    return session;
  }
}
