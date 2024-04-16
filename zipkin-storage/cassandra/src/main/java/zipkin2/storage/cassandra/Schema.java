/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.Version;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  static Metadata ensure(CassandraStorage cassandra, CqlSession session) {
    String keyspace = cassandra.keyspace;
    Schema.ensureExists(session, keyspace, cassandra.searchEnabled);
    return validate(cassandra, session);
  }

  static Metadata validate(CassandraStorage cassandra, CqlSession session) {
    String keyspace = cassandra.keyspace;
    KeyspaceMetadata keyspaceMetadata = ensureKeyspaceMetadata(session, keyspace);

    Map<String, String> replication = keyspaceMetadata.getReplication();
    if ("SimpleStrategy".equals(replication.get("class"))) {
      if ("1".equals(replication.get("replication_factor"))) {
        LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
      }
    }

    boolean hasAutocompleteTags = hasUpgrade1_autocompleteTags(keyspaceMetadata);
    boolean hasRemoteService = hasUpgrade2_remoteService(keyspaceMetadata);

    Metadata md = new Metadata(hasAutocompleteTags, hasRemoteService);
    // Begin validation of externally provided schema.
    if (!has_schema(keyspaceMetadata)) {
      logAndThrow("schema not installed: apply %s, or set CASSANDRA_ENSURE_SCHEMA=true",
        SCHEMA_RESOURCE);
    }

    if (!cassandra.searchEnabled) {
      return md;
    }

    if (!has_indexing(keyspaceMetadata)) {
      logAndThrow(
        "schema lacks indexing: apply %s, or set CASSANDRA_ENSURE_SCHEMA=true", INDEX_RESOURCE);
    }

    // Don't throw on more esoteric features
    if (!hasAutocompleteTags) {
      LOG.warn(
        "schema lacks autocomplete indexing: apply {}, or set CASSANDRA_ENSURE_SCHEMA=true",
        UPGRADE_1);
    }

    if (!hasRemoteService) {
      LOG.warn(
        "schema lacks remote service indexing: apply {}, or set CASSANDRA_ENSURE_SCHEMA=true",
        UPGRADE_2);
    }

    return md;
  }

  static void logAndThrow(String messageFormat, Object... args) {
    String message = messageFormat.formatted(args);
    // Ensure we can look at logs to see the problem. Otherwise, it may only
    // be visible in API error responses, such as /health or /api/v2/traces.
    LOG.error(message);
    throw new RuntimeException(message);
  }

  static void initializeUDTs(CqlSession session, String keyspace) {
    KeyspaceMetadata ks = session.getMetadata().getKeyspace(keyspace).get();
    MutableCodecRegistry codecRegistry =
      (MutableCodecRegistry) session.getContext().getCodecRegistry();

    TypeCodec<UdtValue> annotationUDTCodec =
      codecRegistry.codecFor(ks.getUserDefinedType("annotation").get());
    codecRegistry.register(new AnnotationCodec(annotationUDTCodec));

    LOG.debug("Registering endpoint and annotation UDTs to keyspace {}", keyspace);
    TypeCodec<UdtValue> endpointUDTCodec =
      codecRegistry.codecFor(ks.getUserDefinedType("endpoint").get());
    codecRegistry.register(new EndpointCodec(endpointUDTCodec));
  }

  static final class Metadata {
    final boolean hasAutocompleteTags, hasRemoteService;

    Metadata(boolean hasAutocompleteTags, boolean hasRemoteService) {
      this.hasAutocompleteTags = hasAutocompleteTags;
      this.hasRemoteService = hasRemoteService;
    }
  }

  static KeyspaceMetadata ensureKeyspaceMetadata(CqlSession session, String keyspace) {
    KeyspaceMetadata keyspaceMetadata = session.getMetadata().getKeyspace(keyspace).orElse(null);
    if (keyspaceMetadata == null) {
      throw new IllegalStateException(
        
          "Cannot read keyspace metadata for keyspace: %s and cluster: %s".formatted(
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
        throw new RuntimeException(
          "Node %s is running Cassandra %s, but minimum version is 3.11.3".formatted(
          entry.getKey(), entry.getValue().getCassandraVersion()));
      }
    }
    if (version == null) throw new RuntimeException("No nodes in the cluster");
    LOG.info("Detected Cassandra version {}", version);
    return version;
  }

  static void ensureExists(CqlSession session, String keyspace, boolean searchEnabled) {
    KeyspaceMetadata result = session.getMetadata().getKeyspace(keyspace).orElse(null);
    Version version = ensureVersion(session.getMetadata());
    if (result == null || result.getTable(Schema.TABLE_SPAN).isEmpty()) {
      LOG.info("Installing schema {} for keyspace {}", SCHEMA_RESOURCE, keyspace);
      applyCqlFile(version, keyspace, session, SCHEMA_RESOURCE);
      if (searchEnabled) {
        LOG.info("Installing indexes {} for keyspace {}", INDEX_RESOURCE, keyspace);
        applyCqlFile(version, keyspace, session, INDEX_RESOURCE);
      }
    } else if (searchEnabled) { // prior installation
      if (!hasUpgrade1_autocompleteTags(result)) {
        LOG.info("Upgrading schema {}", UPGRADE_1);
        applyCqlFile(version, keyspace, session, UPGRADE_1);
      }
      if (!hasUpgrade2_remoteService(result)) {
        LOG.info("Upgrading schema {}", UPGRADE_2);
        applyCqlFile(version, keyspace, session, UPGRADE_2);
      }
    }
  }

  static boolean has_schema(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_SPAN).isPresent();
  }

  static boolean has_indexing(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_SERVICE_SPANS).isPresent();
  }

  static boolean hasUpgrade1_autocompleteTags(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_AUTOCOMPLETE_TAGS).isPresent();
  }

  static boolean hasUpgrade2_remoteService(KeyspaceMetadata keyspaceMetadata) {
    return keyspaceMetadata.getTable(TABLE_SERVICE_REMOTE_SERVICES).isPresent();
  }

  static void applyCqlFile(Version version, String keyspace, CqlSession session, String resource) {
    for (String cmd : resourceToString(resource).split(";", 100)) {
      cmd = cmd.trim().replace(" " + DEFAULT_KEYSPACE, " " + keyspace);
      if (cmd.isEmpty()) continue;
      cmd = reviseCQL(version, cmd);
      try {
        session.execute(cmd);
      } catch (InvalidQueryException e) {
        // Add context so it is obvious which line was wrong
        String message = "Failed to execute [%s]: %s".formatted(cmd, e.getMessage());
        // Ensure we can look at logs to see the problem. Otherwise, it may only
        // be visible in API error responses, such as /health or /api/v2/traces.
        LOG.error(message);
        throw new RuntimeException(message, e);
      }
    }
  }

  static String reviseCQL(Version version, String cql) {
    if (version.getMajor() >= 4) {
      // read_repair_chance options were removed and make Cassandra crash starting in v4
      // See https://cassandra.apache.org/doc/latest/operating/read_repair.html#background-read-repair
      cql = cql.replaceAll(" *AND [^\\s]*read_repair_chance = 0\n", "");
    }
    return cql;
  }

  Schema() {
  }
}
