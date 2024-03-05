/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.Version;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.TestInfo;
import zipkin2.DependencyLink;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_REMOTE_SERVICES;

class InternalForTests {
  static CqlSession mockSession() {
    CqlSession session = mock(CqlSession.class);
    Metadata metadata = mock(Metadata.class);
    Node node = mock(Node.class);

    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getNodes()).thenReturn(Map.of(
      UUID.fromString("11111111-1111-1111-1111-111111111111"), node
    ));
    when(node.getCassandraVersion()).thenReturn(Version.parse("3.11.9"));

    KeyspaceMetadata keyspaceMetadata = mock(KeyspaceMetadata.class);
    when(session.getMetadata()).thenReturn(metadata);
    when(metadata.getKeyspace("zipkin2")).thenReturn(Optional.of(keyspaceMetadata));

    when(keyspaceMetadata.getTable(TABLE_SERVICE_REMOTE_SERVICES))
      .thenReturn(Optional.of(mock(TableMetadata.class)));
    return session;
  }

  static void writeDependencyLinks(
    CassandraStorage storage, List<DependencyLink> links, long midnightUTC) {
    CqlSession session = storage.session();
    PreparedStatement prepared = session.prepare("INSERT INTO " + Schema.TABLE_DEPENDENCY
      + " (day,parent,child,calls,errors)"
      + " VALUES (?,?,?,?,?)");
    LocalDate day = Instant.ofEpochMilli(midnightUTC).atZone(ZoneOffset.UTC).toLocalDate();
    for (DependencyLink link : links) {
      int i = 0;
      storage.session().execute(prepared.bind()
        .setLocalDate(i++, day)
        .setString(i++, link.parent())
        .setString(i++, link.child())
        .setLong(i++, link.callCount())
        .setLong(i, link.errorCount()));
    }
  }

  static String keyspace(TestInfo testInfo) {
    String result;
    if (testInfo.getTestMethod().isPresent()) {
      result = testInfo.getTestMethod().get().getName();
    } else {
      assert testInfo.getTestClass().isPresent();
      result = testInfo.getTestClass().get().getSimpleName();
    }
    result = result.toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
