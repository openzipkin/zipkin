/*
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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.rules.TestName;
import zipkin2.internal.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalForTests {

  public static void writeDependencyLinks(
      CassandraStorage storage, List<zipkin.DependencyLink> links, long midnightUTC) {
    List<zipkin2.DependencyLink> converted = new ArrayList<>();
    for (zipkin.DependencyLink link : links) {
      converted.add(
          zipkin2.DependencyLink.newBuilder()
              .parent(link.parent)
              .child(link.child)
              .callCount(link.callCount)
              .errorCount(link.errorCount)
              .build());
    }
    Dependencies deps = Dependencies.create(midnightUTC, midnightUTC /* ignored */, converted);
    ByteBuffer thrift = deps.toThrift();
    Insert statement =
        QueryBuilder.insertInto("dependencies")
            .value("day", new Date(midnightUTC))
            .value("dependencies", thrift);
    storage.session().execute(statement);
  }

  public static Session session(CassandraStorage storage) {
    return storage.session();
  }

  public static void ensureExists(String keyspace, Session session) {
    Schema.ensureExists(keyspace, session);
  }

  public static boolean hasUpgrade1_defaultTtl(KeyspaceMetadata metadata) {
    return Schema.hasUpgrade1_defaultTtl(metadata);
  }

  public static void applyCqlFile(String keyspace, Session session, String resource) {
    Schema.applyCqlFile(keyspace, session, resource);
  }

  public static void blockWhileInFlight(CassandraStorage storage) {
    // Now, block until writes complete, notably so we can read them.
    Session.State state = storage.session().getState();
    refresh:
    while (true) {
      for (Host host : state.getConnectedHosts()) {
        if (state.getInFlightQueries(host) > 0) {
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
          state = storage.session().getState();
          continue refresh;
        }
      }
      break;
    }
  }

  public static int indexFetchMultiplier(CassandraStorage storage) {
    return storage.indexFetchMultiplier;
  }

  public static String keyspace(TestName testName) {
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }

  public static void dropKeyspace(Session session, String keyspace) {
    session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
    assertThat(session.getCluster().getMetadata().getKeyspace(keyspace)).isNull();
  }
}
