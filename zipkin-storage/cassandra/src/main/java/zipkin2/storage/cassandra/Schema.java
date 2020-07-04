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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.VersionNumber;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.internal.Nullable;

import static zipkin2.storage.cassandra.internal.Resources.resourceToString;

final class Schema {
  static final Logger LOG = LoggerFactory.getLogger(Schema.class);

  static final String TABLE_SPAN = "span";
  static final String TABLE_TRACE_BY_SERVICE_SPAN = "trace_by_service_span";
  static final String TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE = "trace_by_service_remote_service";
  static final String TABLE_SERVICE_SPANS = "span_by_service";
  static final String TABLE_SERVICE_REMOTE_SERVICES = "remote_service_by_service";
  static final String TABLE_DEPENDENCY = "dependency";
  static final String TABLE_AUTOCOMPLETE_TAGS = "autocomplete_tags";

  static final String DEFAULT_KEYSPACE = "zipkin2";
  static final String SCHEMA_RESOURCE = "/zipkin2-schema.cql";
  static final String INDEX_RESOURCE = "/zipkin2-schema-indexes.cql";
  static final String UPGRADE_1 = "/zipkin2-schema-upgrade-1.cql";
  static final String UPGRADE_2 = "/zipkin2-schema-upgrade-2.cql";

  Schema() {
  }

  static Metadata readMetadata(Session session) {
    KeyspaceMetadata keyspaceMetadata =
      ensureKeyspaceMetadata(session, session.getLoggedKeyspace());

    Map<String, String> replication = keyspaceMetadata.getReplication();
    if ("SimpleStrategy".equals(replication.get("class"))) {
      if ("1".equals(replication.get("replication_factor"))) {
        LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
      }

      ConsistencyLevel cl =
        session.getCluster().getConfiguration().getQueryOptions().getConsistencyLevel();

      if (cl != ConsistencyLevel.ONE) {
        throw new IllegalArgumentException("Do not define `local_dc` and use SimpleStrategy");
      }
    }
    String compactionClass =
      keyspaceMetadata.getTable("span").getOptions().getCompaction().get("class");

    boolean hasAutocompleteTags = hasUpgrade1_autocompleteTags(keyspaceMetadata);
    if (!hasAutocompleteTags) {
      LOG.warn(
        "schema lacks autocomplete indexing: apply {}, or set CassandraStorage.ensureSchema=true",
        UPGRADE_1);
    }

    boolean hasRemoteService = hasUpgrade2_remoteService(keyspaceMetadata);
    if (!hasRemoteService) {
      LOG.warn(
        "schema lacks remote service indexing: apply {}, or set CassandraStorage.ensureSchema=true",
        UPGRADE_2);
    }

    return new Metadata(compactionClass, hasAutocompleteTags, hasRemoteService);
  }

  static final class Metadata {
    final String compactionClass;
    final boolean hasAutocompleteTags, hasRemoteService;

    Metadata(String compactionClass, boolean hasAutocompleteTags,
      boolean hasRemoteService) {
      this.compactionClass = compactionClass;
      this.hasAutocompleteTags = hasAutocompleteTags;
      this.hasRemoteService = hasRemoteService;
    }
  }

  static KeyspaceMetadata ensureKeyspaceMetadata(Session session, String keyspace) {
    KeyspaceMetadata keyspaceMetadata = getKeyspaceMetadata(session, keyspace);
    if (keyspaceMetadata == null) {
      throw new IllegalStateException(
        String.format(
          "Cannot read keyspace metadata for keyspace: %s and cluster: %s",
          keyspace, session.getCluster().getClusterName()));
    }
    return keyspaceMetadata;
  }

  @Nullable static KeyspaceMetadata getKeyspaceMetadata(Session session, String keyspace) {
    Cluster cluster = session.getCluster();
    com.datastax.driver.core.Metadata metadata = cluster.getMetadata();
    for (Host node : metadata.getAllHosts()) {
      VersionNumber version = node.getCassandraVersion();
      if (version == null) throw new RuntimeException("node had no version: " + node);
      if (VersionNumber.parse("3.11.3").compareTo(version) > 0) {
        throw new RuntimeException(String.format(
          "Host %s is running Cassandra %s, but minimum version is 3.11.3",
          node.getHostId(), node.getCassandraVersion()));
      }
    }
    return metadata.getKeyspace(keyspace);
  }

  static KeyspaceMetadata ensureExists(String keyspace, boolean searchEnabled, Session session) {
    KeyspaceMetadata result = getKeyspaceMetadata(session, keyspace);
    if (result == null || result.getTable(Schema.TABLE_SPAN) == null) {
      LOG.info("Installing schema {} for keyspace {}", SCHEMA_RESOURCE, keyspace);
      applyCqlFile(keyspace, session, SCHEMA_RESOURCE);
      if (searchEnabled) {
        LOG.info("Installing indexes {} for keyspace {}", INDEX_RESOURCE, keyspace);
        applyCqlFile(keyspace, session, INDEX_RESOURCE);
      }
      // refresh metadata since we've installed the schema
      result = ensureKeyspaceMetadata(session, keyspace);
    }
    if (!hasUpgrade1_autocompleteTags(result)) {
      LOG.info("Upgrading schema {}", UPGRADE_1);
      applyCqlFile(keyspace, session, UPGRADE_1);
    }
    if (!hasUpgrade2_remoteService(result)) {
      LOG.info("Upgrading schema {}", UPGRADE_2);
      applyCqlFile(keyspace, session, UPGRADE_2);
    }
    return result;
  }

  static boolean hasUpgrade1_autocompleteTags(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_AUTOCOMPLETE_TAGS) != null;
  }

  static boolean hasUpgrade2_remoteService(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_SERVICE_REMOTE_SERVICES) != null;
  }

  static void applyCqlFile(String keyspace, Session session, String resource) {
    for (String cmd : resourceToString(resource).split(";", 100)) {
      cmd = cmd.trim().replace(" " + DEFAULT_KEYSPACE, " " + keyspace);
      if (!cmd.isEmpty()) {
        session.execute(cmd);
      }
    }
  }
}
