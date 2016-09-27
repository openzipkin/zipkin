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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.google.common.io.Closer;
import java.io.IOException;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.storage.cassandra3.DefaultSessionFactory.buildCluster;

public final class EnsureSchemaTest {

  @Rule
  public TestName name = new TestName();

  @BeforeClass public static void checkCassandraIsUp() {
    try (Cluster cluster = buildCluster(Cassandra3Storage.builder().build());
         Session session = cluster.newSession()) {
      session.execute("SELECT now() FROM system.local");
    } catch (RuntimeException e) {
      throw new AssumptionViolatedException(e.getMessage(), e);
    }
  }

  Closer closer = Closer.create();
  String keyspace;
  Cluster cluster;
  Session session;

  @Before
  public void connectAndDropKeyspace() {
    keyspace = name.getMethodName().toLowerCase();
    cluster = closer.register(buildCluster(Cassandra3Storage.builder().keyspace(keyspace).build()));
    session = closer.register(cluster.newSession());
    session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
    assertThat(session.getCluster().getMetadata().getKeyspace(keyspace)).isNull();
  }

  @After
  public void close() throws IOException {
    closer.close();
  }

  @Test public void installsKeyspaceWhenMissing() {
    Schema.ensureExists(keyspace, session);

    KeyspaceMetadata metadata = session.getCluster().getMetadata().getKeyspace(keyspace);
    assertThat(metadata).isNotNull();
  }

  @Test public void installsTablesWhenMissing() {
    session.execute("CREATE KEYSPACE " + keyspace
        + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};");

    Schema.ensureExists(keyspace, session);

    KeyspaceMetadata metadata = session.getCluster().getMetadata().getKeyspace(keyspace);
    assertThat(metadata).isNotNull();
  }
}
