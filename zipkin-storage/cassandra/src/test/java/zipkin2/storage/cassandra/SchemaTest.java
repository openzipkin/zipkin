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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.Version;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaTest {
  @Test public void ensureKeyspaceMetadata_failsWhenVersionLessThan3_11_3() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(Collections.singletonMap(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.2"));

    assertThatThrownBy(() -> Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isInstanceOf(RuntimeException.class)
      .hasMessage(
        "Node 11111111-1111-1111-1111-111111111111 is running Cassandra 3.11.2, but minimum version is 3.11.3");
  }

  @Test public void ensureKeyspaceMetadata_failsWhenOneVersionLessThan3_11_3() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node1 = mock(Node.class);
    Node node2 = mock(Node.class);
    Map<UUID, Node> nodes = new LinkedHashMap<>();
    nodes.put(UUID.fromString("11111111-1111-1111-1111-111111111111"), node1);
    nodes.put(UUID.fromString("22222222-2222-2222-2222-222222222222"), node2);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(nodes);
    when(node1.getCassandraVersion()).thenReturn(Version.parse("3.11.3"));
    when(node2.getCassandraVersion()).thenReturn(Version.parse("3.11.2"));

    assertThatThrownBy(() -> Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isInstanceOf(RuntimeException.class)
      .hasMessage(
        "Node 22222222-2222-2222-2222-222222222222 is running Cassandra 3.11.2, but minimum version is 3.11.3");
  }

  @Test public void ensureKeyspaceMetadata_passesWhenVersion3_11_3AndKeyspaceMetadataIsNotNull() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);
    KeyspaceMetadata keyspaceMetadata = mock(KeyspaceMetadata.class);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(Collections.singletonMap(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.3"));
    when(metadata.getKeyspace("zipkin2")).thenReturn(Optional.of(keyspaceMetadata));

    assertThat(Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isSameAs(keyspaceMetadata);
  }

  @Test public void ensureKeyspaceMetadata_passesWhenVersion3_11_4AndKeyspaceMetadataIsNotNull() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);
    KeyspaceMetadata keyspaceMetadata = mock(KeyspaceMetadata.class);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(Collections.singletonMap(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.4"));
    when(metadata.getKeyspace("zipkin2")).thenReturn(Optional.of(keyspaceMetadata));

    assertThat(Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isSameAs(keyspaceMetadata);
  }

  @Test public void ensureKeyspaceMetadata_failsWhenKeyspaceMetadataIsNotNull() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(Collections.singletonMap(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.3"));

    assertThatThrownBy(() -> Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isInstanceOf(RuntimeException.class)
      .hasMessageStartingWith("Cannot read keyspace metadata for keyspace");
  }

  String schemaWithReadRepair = ""
    + "CREATE TABLE IF NOT EXISTS zipkin2.remote_service_by_service (\n"
    + "    service text,\n"
    + "    remote_service text,\n"
    + "    PRIMARY KEY (service, remote_service)\n"
    + ")\n"
    + "    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}\n"
    + "    AND caching = {'rows_per_partition': 'ALL'}\n"
    + "    AND default_time_to_live =  259200\n"
    + "    AND gc_grace_seconds = 3600\n"
    + "    AND read_repair_chance = 0\n"
    + "    AND dclocal_read_repair_chance = 0\n"
    + "    AND speculative_retry = '95percentile'\n"
    + "    AND comment = 'Secondary table for looking up remote service names by a service name.';";

  @Test public void reviseCql_leaves_read_repair_chance_on_v3() {
    assertThat(Schema.reviseCQL(Version.parse("3.11.9"), schemaWithReadRepair))
      .isSameAs(schemaWithReadRepair);
  }

  @Test public void reviseCql_removes_dclocal_read_repair_chance_on_v4() {
    assertThat(Schema.reviseCQL(Version.V4_0_0, schemaWithReadRepair))
      // literal used to show newlines etc are in-tact
      .isEqualTo(""
        + "CREATE TABLE IF NOT EXISTS zipkin2.remote_service_by_service (\n"
        + "    service text,\n"
        + "    remote_service text,\n"
        + "    PRIMARY KEY (service, remote_service)\n"
        + ")\n"
        + "    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}\n"
        + "    AND caching = {'rows_per_partition': 'ALL'}\n"
        + "    AND default_time_to_live =  259200\n"
        + "    AND gc_grace_seconds = 3600\n"
        + "    AND speculative_retry = '95percentile'\n"
        + "    AND comment = 'Secondary table for looking up remote service names by a service name.';");
  }
}
