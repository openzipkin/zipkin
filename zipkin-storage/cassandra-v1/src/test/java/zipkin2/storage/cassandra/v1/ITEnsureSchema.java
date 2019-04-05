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

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import java.net.InetSocketAddress;
import org.junit.Test;
import zipkin2.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

abstract class ITEnsureSchema {

  abstract String keyspace();

  abstract Session session();

  abstract InetSocketAddress contactPoint();

  @Test public void installsKeyspaceWhenMissing() {
    Schema.ensureExists(keyspace(), session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
    assertThat(Schema.hasUpgrade1_defaultTtl(metadata)).isTrue();
  }

  @Test public void installsTablesWhenMissing() {
    session().execute("CREATE KEYSPACE " + keyspace()
      + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};");

    Schema.ensureExists(keyspace(), session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
    assertThat(Schema.hasUpgrade1_defaultTtl(metadata)).isTrue();
    assertThat(metadata.getTable("autocomplete_tags")).isNotNull();
  }

  @Test public void upgradesOldSchema() {
    Schema.applyCqlFile(keyspace(), session(), "/cassandra-schema-cql3-original.txt");

    Schema.ensureExists(keyspace(), session());

    KeyspaceMetadata metadata = session().getCluster().getMetadata().getKeyspace(keyspace());
    assertThat(metadata).isNotNull();
    assertThat(Schema.hasUpgrade1_defaultTtl(metadata)).isTrue();
    assertThat(Schema.hasUpgrade2_autocompleteTags(metadata)).isTrue();
  }

  /** This tests we don't accidentally rely on new indexes such as autocomplete tags */
  @Test public void worksWithOldSchema() throws Exception {
    Schema.applyCqlFile(keyspace(), session(), "/cassandra-schema-cql3-original.txt");

    InetSocketAddress contactPoint = contactPoint();
    try (CassandraStorage storage = CassandraStorage.newBuilder()
      .contactPoints(contactPoint.getHostString() + ":" + contactPoint.getPort())
      .ensureSchema(false)
      .autocompleteKeys(asList("environment"))
      .keyspace(keyspace()).build()) {

      storage.spanConsumer().accept(TestObjects.TRACE).execute();

      assertThat(storage.spanStore().getTrace(TestObjects.TRACE.get(0).traceId()).execute())
        .containsExactlyInAnyOrderElementsOf(TestObjects.TRACE);

      assertThat(storage.autocompleteTags().getValues("environment").execute())
        .isEmpty(); // instead of an exception
      String serviceName = TestObjects.TRACE.get(0).localServiceName();
      assertThat(storage.serviceAndSpanNames().getRemoteServiceNames(serviceName).execute())
        .isEmpty(); // instead of an exception
    }
  }
}
