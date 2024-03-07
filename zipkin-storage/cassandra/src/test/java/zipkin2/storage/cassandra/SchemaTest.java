/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.Version;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaTest {
  @Test void ensureVersion_failsWhenVersionLessThan3_11_3() {
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(metadata.getNodes()).thenReturn(Map.of(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.2"));

    assertThatThrownBy(() -> Schema.ensureVersion(metadata))
      .isInstanceOf(RuntimeException.class)
      .hasMessage(
        "Node 11111111-1111-1111-1111-111111111111 is running Cassandra 3.11.2, but minimum version is 3.11.3");
  }

  @Test void ensureVersion_failsWhenOneVersionLessThan3_11_3() {
    Metadata metadata = mock(Metadata.class);
    Node node1 = mock(Node.class);
    Node node2 = mock(Node.class);
    Map<UUID, Node> nodes = new LinkedHashMap<>();
    nodes.put(UUID.fromString("11111111-1111-1111-1111-111111111111"), node1);
    nodes.put(UUID.fromString("22222222-2222-2222-2222-222222222222"), node2);

    when(metadata.getNodes()).thenReturn(nodes);
    when(node1.getCassandraVersion()).thenReturn(Version.parse("3.11.3"));
    when(node2.getCassandraVersion()).thenReturn(Version.parse("3.11.2"));

    assertThatThrownBy(() -> Schema.ensureVersion(metadata))
      .isInstanceOf(RuntimeException.class)
      .hasMessage(
        "Node 22222222-2222-2222-2222-222222222222 is running Cassandra 3.11.2, but minimum version is 3.11.3");
  }

  @Test void ensureVersion_passesWhenVersion3_11_3() {
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(metadata.getNodes()).thenReturn(Map.of(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.3"));

    assertThat(Schema.ensureVersion(metadata))
      .isEqualTo(Version.parse("3.11.3"));
  }

  @Test void ensureVersion_passesWhenVersion3_11_4() {
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(metadata.getNodes()).thenReturn(Map.of(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.4"));

    assertThat(Schema.ensureVersion(metadata))
      .isEqualTo(Version.parse("3.11.4"));
  }

  @Test void ensureKeyspaceMetadata() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    when(session.getMetadata()).thenReturn(metadata);
    KeyspaceMetadata keyspaceMetadata = mock(KeyspaceMetadata.class);
    when(metadata.getKeyspace("zipkin2")).thenReturn(Optional.of(keyspaceMetadata));

    assertThat(Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isSameAs(keyspaceMetadata);
  }

  @Test void ensureKeyspaceMetadata_failsWhenKeyspaceMetadataIsNull() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);

    when(session.getMetadata()).thenReturn(metadata);

    assertThatThrownBy(() -> Schema.ensureKeyspaceMetadata(session, "zipkin2"))
      .isInstanceOf(RuntimeException.class)
      .hasMessageStartingWith("Cannot read keyspace metadata for keyspace");
  }

  String schemaWithReadRepair = """
    CREATE TABLE IF NOT EXISTS zipkin2.remote_service_by_service (
        service text,
        remote_service text,
        PRIMARY KEY (service, remote_service)
    )
        WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
        AND caching = {'rows_per_partition': 'ALL'}
        AND default_time_to_live =  259200
        AND gc_grace_seconds = 3600
        AND read_repair_chance = 0
        AND dclocal_read_repair_chance = 0
        AND speculative_retry = '95percentile'
        AND comment = 'Secondary table for looking up remote service names by a service name.';\
    """;

  @Test void reviseCql_leaves_read_repair_chance_on_v3() {
    assertThat(Schema.reviseCQL(Version.parse("3.11.9"), schemaWithReadRepair))
      .isSameAs(schemaWithReadRepair);
  }

  @Test void reviseCql_removes_dclocal_read_repair_chance_on_v4() {
    assertThat(Schema.reviseCQL(Version.V4_0_0, schemaWithReadRepair))
      // literal used to show newlines etc. are in-tact
      .isEqualTo("""
        CREATE TABLE IF NOT EXISTS zipkin2.remote_service_by_service (
            service text,
            remote_service text,
            PRIMARY KEY (service, remote_service)
        )
            WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
            AND caching = {'rows_per_partition': 'ALL'}
            AND default_time_to_live =  259200
            AND gc_grace_seconds = 3600
            AND speculative_retry = '95percentile'
            AND comment = 'Secondary table for looking up remote service names by a service name.';\
        """);
  }
}
