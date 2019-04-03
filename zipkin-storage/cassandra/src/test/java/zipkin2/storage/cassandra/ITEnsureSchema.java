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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import java.net.InetSocketAddress;
import org.junit.Test;
import zipkin2.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ITEnsureSchema {

  abstract protected String keyspace();

  abstract protected Session session();

  abstract InetSocketAddress contactPoint();

  @Test public void installsKeyspaceWhenMissing() {
    Schema.ensureExists(keyspace(), false, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
  }

  @Test public void installsTablesWhenMissing() {
    session().execute("CREATE KEYSPACE " + keyspace()
      + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};");

    Schema.ensureExists(keyspace(), false, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata.getTable("span")).isNotNull();
  }

  @Test public void installsIndexesWhenMissing() {
    session().execute("CREATE KEYSPACE " + keyspace()
      + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};");

    Schema.ensureExists(keyspace(), true, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata.getTable("trace_by_service_span")).isNotNull();
    assertThat(metadata.getTable("autocomplete_tags")).isNotNull();
  }

  @Test public void upgradesOldSchema_autocomplete() {
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema.cql");
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema-indexes-original.cql");

    Schema.ensureExists(keyspace(), true, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
    assertThat(Schema.hasUpgrade1_autocompleteTags(metadata)).isTrue();
  }

  @Test public void upgradesOldSchema_remoteService() {
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema.cql");
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema-indexes-original.cql");
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema-upgrade-1.cql");

    Schema.ensureExists(keyspace(), true, session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
    assertThat(Schema.hasUpgrade2_remoteService(metadata)).isTrue();
  }

  /** This tests we don't accidentally rely on new indexes such as autocomplete tags */
  @Test public void worksWithOldSchema() throws Exception {
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema.cql");
    Schema.applyCqlFile(keyspace(), session(), "/zipkin2-schema-indexes-original.cql");

    InetSocketAddress contactPoint = contactPoint();
    try (CassandraStorage storage = CassandraStorage.newBuilder()
      .contactPoints(contactPoint.getHostString() + ":" + contactPoint.getPort())
      .ensureSchema(false)
      .keyspace(keyspace()).build()) {

      storage.spanConsumer().accept(TestObjects.TRACE).execute();

      assertThat(storage.spanStore().getTrace(TestObjects.TRACE.get(0).traceId()).execute())
        .containsExactlyInAnyOrderElementsOf(TestObjects.TRACE);
    }
  }
}
