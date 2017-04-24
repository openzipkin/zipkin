/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.cassandra3.integration;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin.storage.cassandra3.InternalForTests;

import static org.assertj.core.api.Assertions.assertThat;

abstract class CassandraEnsureSchemaTest {

  abstract protected TestName name();

  abstract protected Session session();

  private String keyspace;

  @Before
  public void connectAndDropKeyspace() {
    keyspace = name().getMethodName().toLowerCase();
    session().execute("DROP KEYSPACE IF EXISTS " + keyspace);
    assertThat(session().getCluster().getMetadata().getKeyspace(keyspace)).isNull();
  }

  @Test public void installsKeyspaceWhenMissing() {
    InternalForTests.ensureExists(keyspace, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace);
    assertThat(metadata).isNotNull();
  }

  @Test public void installsTablesWhenMissing() {
    session().execute("CREATE KEYSPACE " + keyspace
      + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};");

    InternalForTests.ensureExists(keyspace, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace);
    assertThat(metadata).isNotNull();
  }
}
