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
package zipkin.storage.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static zipkin.internal.Util.UTF_8;

final class Schema {
  private static final Logger LOG = LoggerFactory.getLogger(Schema.class);

  private static final String SCHEMA = "/cassandra-schema-cql3.txt";

  private static final String UPGRADE_1 = "/cassandra-schema-cql3-upgrade-1.txt";

  private Schema() {
  }

  static Metadata readMetadata(Session session) {
    KeyspaceMetadata keyspaceMetadata = getKeyspaceMetadata(session);

    Map<String, String> replication = keyspaceMetadata.getReplication();
    if ("SimpleStrategy".equals(replication.get("class")) && "1".equals(
        replication.get("replication_factor"))) {
      LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
    }
    String compactionClass =
        keyspaceMetadata.getTable("traces").getOptions().getCompaction().get("class");
    boolean hasDefaultTtl = hasUpgrade1_defaultTtl(keyspaceMetadata);
    if (!hasDefaultTtl) {
      LOG.warn("schema lacks default ttls: apply {}, or set CassandraStorage.ensureSchema=true",
          UPGRADE_1);
    }
    return new Metadata(compactionClass, hasDefaultTtl);
  }

  static final class Metadata {
    final String compactionClass;
    final boolean hasDefaultTtl;

    Metadata(String compactionClass, boolean hasDefaultTtl) {
      this.compactionClass = compactionClass;
      this.hasDefaultTtl = hasDefaultTtl;
    }
  }

  static KeyspaceMetadata getKeyspaceMetadata(Session session) {
    String keyspace = session.getLoggedKeyspace();
    Cluster cluster = session.getCluster();
    KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);

    if (keyspaceMetadata == null) {
      throw new IllegalStateException(String.format(
          "Cannot read keyspace metadata for give keyspace: %s and cluster: %s",
          keyspace, cluster.getClusterName()));
    }
    return keyspaceMetadata;
  }

  static void ensureExists(String keyspace, Session session) {
    KeyspaceMetadata keyspaceMetadata = session.getCluster().getMetadata().getKeyspace(keyspace);
    if (keyspaceMetadata == null || keyspaceMetadata.getTable("traces") == null) {
      LOG.info("Installing schema {}", SCHEMA);
      applyCqlFile(keyspace, session, SCHEMA);
      // refresh metadata since we've installed the schema
      keyspaceMetadata = session.getCluster().getMetadata().getKeyspace(keyspace);
    }
    if (!hasUpgrade1_defaultTtl(keyspaceMetadata)) {
      LOG.info("Upgrading schema {}", UPGRADE_1);
      applyCqlFile(keyspace, session, UPGRADE_1);
    }
  }

  static boolean hasUpgrade1_defaultTtl(KeyspaceMetadata keyspaceMetadata) {
    // TODO: we need some approach to forward-check compatibility as well.
    //  backward: this code knows the current schema is too old.
    //  forward:  this code knows the current schema is too new.
    return keyspaceMetadata.getTable("traces").getOptions().getDefaultTimeToLive() > 0;
  }

  static void applyCqlFile(String keyspace, Session session, String resource) {
    try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(resource), UTF_8)) {
      for (String cmd : CharStreams.toString(reader).split(";")) {
        cmd = cmd.trim().replace(" zipkin", " " + keyspace);
        if (!cmd.isEmpty()) {
          session.execute(cmd);
        }
      }
    } catch (IOException ex) {
      LOG.error(ex.getMessage(), ex);
    }
  }
}
