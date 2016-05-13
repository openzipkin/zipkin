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
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Schema {
  private static final Logger LOG = LoggerFactory.getLogger(Schema.class);

  private static final String SCHEMA = "/cassandra-schema-cql3.txt";

  private Schema() {
  }

  static Map<String, String> readMetadata(Session session) {
    Map<String, String> metadata = new LinkedHashMap<>();
    KeyspaceMetadata keyspaceMetadata =
        getKeyspaceMetadata(session.getLoggedKeyspace(), session.getCluster());

    Map<String, String> replication = keyspaceMetadata.getReplication();
    if ("SimpleStrategy".equals(replication.get("class")) && "1".equals(
        replication.get("replication_factor"))) {
      LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
    }
    Map<String, String> tracesCompaction =
        keyspaceMetadata.getTable("traces").getOptions().getCompaction();
    metadata.put("traces.compaction.class", tracesCompaction.get("class"));
    return metadata;
  }

  private static KeyspaceMetadata getKeyspaceMetadata(String keyspace, Cluster cluster) {
    KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);

    if (keyspaceMetadata == null) {
      throw new IllegalStateException(String.format(
          "Cannot read keyspace metadata for give keyspace: %s and cluster: %s",
          keyspace, cluster.getClusterName()));
    }
    return keyspaceMetadata;
  }

  static void ensureExists(String keyspace, Session session) {
    try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(SCHEMA))) {
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
