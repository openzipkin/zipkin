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
package zipkin.server.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.Version;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static zipkin.server.storage.cassandra.Resources.resourceToString;

final class Schema {
  static final Logger LOG = LoggerFactory.getLogger(Schema.class);

  static final String DEFAULT_KEYSPACE = "zipkin2";
  static final String SCHEMA_RESOURCE = "/zipkin-schemas.cql";

  static void check(CqlSession session, String keyspace) {
    KeyspaceMetadata keyspaceMetadata = ensureKeyspaceMetadata(session, keyspace);

    Map<String, String> replication = keyspaceMetadata.getReplication();
    final String replicationClass = replication.get("class");
    if (replicationClass != null && replicationClass.endsWith("SimpleStrategy")) {
      if ("1".equals(replication.get("replication_factor"))) {
        LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
      }
    }
  }

  static final class Metadata {
    final boolean hasAutocompleteTags, hasRemoteService;

    Metadata(boolean hasAutocompleteTags, boolean hasRemoteService) {
      this.hasAutocompleteTags = hasAutocompleteTags;
      this.hasRemoteService = hasRemoteService;
    }
  }

  static KeyspaceMetadata ensureKeyspaceMetadata(CqlSession session, String keyspace) {
    ensureVersion(session.getMetadata());
    KeyspaceMetadata keyspaceMetadata = session.getMetadata().getKeyspace(keyspace).orElse(null);
    if (keyspaceMetadata == null) {
      throw new IllegalStateException(
        String.format(
          "Cannot read keyspace metadata for keyspace: %s and cluster: %s",
          keyspace, session.getMetadata().getClusterName()));
    }
    return keyspaceMetadata;
  }

  static Version ensureVersion(com.datastax.oss.driver.api.core.metadata.Metadata metadata) {
    Version version = null;
    for (Map.Entry<UUID, Node> entry : metadata.getNodes().entrySet()) {
      version = entry.getValue().getCassandraVersion();
      if (version == null) throw new RuntimeException("node had no version: " + entry.getValue());
      if (Version.parse("3.11.3").compareTo(version) > 0) {
        throw new RuntimeException(String.format(
          "Node %s is running Cassandra %s, but minimum version is 3.11.3",
          entry.getKey(), entry.getValue().getCassandraVersion()));
      }
    }
    if (version == null) throw new RuntimeException("No nodes in the cluster");
    return version;
  }

  static KeyspaceMetadata ensureExists(String keyspace, CqlSession session) {
    KeyspaceMetadata result = session.getMetadata().getKeyspace(keyspace).orElse(null);
    if (result == null || !result.getTable(ZipkinSpanRecord.INDEX_NAME).isPresent()) {
      LOG.info("Installing schema {} for keyspace {}", SCHEMA_RESOURCE, keyspace);
      applyCqlFile(keyspace, session, SCHEMA_RESOURCE);
      // refresh metadata since we've installed the schema
      result = ensureKeyspaceMetadata(session, keyspace);
    }
    return result;
  }

  static void applyCqlFile(String keyspace, CqlSession session, String resource) {
    Version version = ensureVersion(session.getMetadata());
    for (String cmd : resourceToString(resource).split(";", 100)) {
      cmd = cmd.trim().replace(" " + DEFAULT_KEYSPACE, " " + keyspace);
      if (cmd.isEmpty()) continue;
      cmd = reviseCQL(version, cmd);
      session.execute(cmd);
    }
  }

  static String reviseCQL(Version version, String cql) {
    if (version.getMajor() == 4) {
      // read_repair_chance options were removed and make Cassandra crash starting in v4
      // See https://cassandra.apache.org/doc/latest/operating/read_repair.html#background-read-repair
      cql = cql.replaceAll(" *AND [^\\s]*read_repair_chance = 0\n", "");
    }
    return cql;
  }

  Schema() {
  }
}
