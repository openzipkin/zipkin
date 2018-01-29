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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.rules.TestName;
import zipkin.DependencyLink;
import zipkin2.storage.SpanConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalForTests {

  public static void writeDependencyLinks(CassandraStorage storage, List<DependencyLink> links,
    long midnightUTC) {
    for (DependencyLink link : links) {
      Insert statement = QueryBuilder.insertInto(Schema.TABLE_DEPENDENCY)
        .value("day", LocalDate.fromMillisSinceEpoch(midnightUTC))
        .value("parent", link.parent)
        .value("child", link.child)
        .value("calls", link.callCount)
        .value("errors", link.errorCount);
      storage.session().execute(statement);
    }
  }

  public static int indexFetchMultiplier(CassandraStorage storage) {
    return storage.indexFetchMultiplier();
  }

  public static long rowCountForTraceByServiceSpan(CassandraStorage storage) {
    return storage.session()
      .execute("SELECT COUNT(*) from " + Schema.TABLE_TRACE_BY_SERVICE_SPAN).one().getLong(0);
  }

  public static SpanConsumer withoutStrictTraceId(CassandraStorage storage) {
    return storage.toBuilder().strictTraceId(false).build().spanConsumer();
  }

  public static void ensureExists(String keyspace, boolean searchEnabled, Session session) {
    Schema.ensureExists(keyspace, searchEnabled, session);
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

  public static String keyspace(TestName testName) {
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }

  public static void dropKeyspace(Session session, String keyspace) {
    session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
    assertThat(session.getCluster().getMetadata().getKeyspace(keyspace)).isNull();
  }

  public static Session session(CassandraStorage storage) {
    return storage.session();
  }
}
