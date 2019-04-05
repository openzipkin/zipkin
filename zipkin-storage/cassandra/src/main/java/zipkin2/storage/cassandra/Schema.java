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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.VersionNumber;
import com.datastax.driver.mapping.annotations.UDT;
import com.google.common.io.CharStreams;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Annotation;
import zipkin2.Endpoint;

import static com.google.common.base.Preconditions.checkState;

final class Schema {
  private static final Logger LOG = LoggerFactory.getLogger(Schema.class);
  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final String TABLE_SPAN = "span";
  static final String TABLE_TRACE_BY_SERVICE_SPAN = "trace_by_service_span";
  static final String TABLE_SERVICE_SPANS = "span_by_service";
  static final String TABLE_SERVICE_REMOTE_SERVICES = "remote_service_by_service";
  static final String TABLE_DEPENDENCY = "dependency";
  static final String TABLE_AUTOCOMPLETE_TAGS = "autocomplete_tags";

  static final String DEFAULT_KEYSPACE = "zipkin2";
  static final String SCHEMA_RESOURCE = "/zipkin2-schema.cql";
  static final String INDEX_RESOURCE = "/zipkin2-schema-indexes.cql";
  static final String UPGRADE_1 = "/zipkin2-schema-upgrade-1.cql";
  static final String UPGRADE_2 = "/zipkin2-schema-upgrade-2.cql";

  private Schema() {
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

      checkState(
        ConsistencyLevel.ONE == cl, "Do not define `local_dc` and use SimpleStrategy");
    }
    String compactionClass =
      keyspaceMetadata.getTable("span").getOptions().getCompaction().get("class");

    boolean hasAutocompleteTags = hasUpgrade1_autocompleteTags(keyspaceMetadata);
    if (!hasAutocompleteTags) {
      LOG.warn(
        "schema lacks autocomplete indexing: apply {}, or set CassandraStorage.ensureSchema=true",
        UPGRADE_1);
    }

    boolean hasRemoteServiceByService = hasUpgrade2_remoteService(keyspaceMetadata);
    if (!hasRemoteServiceByService) {
      LOG.warn(
        "schema lacks remote service indexing: apply {}, or set CassandraStorage.ensureSchema=true",
        UPGRADE_2);
    }

    return new Metadata(compactionClass, hasAutocompleteTags, hasRemoteServiceByService);
  }

  static final class Metadata {
    final String compactionClass;
    final boolean hasAutocompleteTags, hasRemoteServiceByService;

    Metadata(String compactionClass, boolean hasAutocompleteTags,
      boolean hasRemoteServiceByService) {
      this.compactionClass = compactionClass;
      this.hasAutocompleteTags = hasAutocompleteTags;
      this.hasRemoteServiceByService = hasRemoteServiceByService;
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

  static KeyspaceMetadata getKeyspaceMetadata(Session session, String keyspace) {
    Cluster cluster = session.getCluster();
    com.datastax.driver.core.Metadata metadata = cluster.getMetadata();
    for (Host host : metadata.getAllHosts()) {
      checkState(
        0 >= VersionNumber.parse("3.11.3").compareTo(host.getCassandraVersion()),
        "Host %s is running Cassandra %s, but minimum version is 3.11.3",
        host.getHostId(), host.getCassandraVersion());
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
    try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(resource), UTF_8)) {
      for (String cmd : CharStreams.toString(reader).split(";")) {
        cmd = cmd.trim().replace(" " + DEFAULT_KEYSPACE, " " + keyspace);
        if (!cmd.isEmpty()) {
          session.execute(cmd);
        }
      }
    } catch (IOException ex) {
      LOG.error(ex.getMessage(), ex);
    }
  }

  @UDT(name = "endpoint")
  static final class EndpointUDT implements Serializable { // for Spark jobs
    private static final long serialVersionUID = 0L;

    private String service;
    private InetAddress ipv4;
    private InetAddress ipv6;
    private int port;

    EndpointUDT() {
      this.service = null;
      this.ipv4 = null;
      this.ipv6 = null;
      this.port = 0;
    }

    EndpointUDT(Endpoint endpoint) {
      this.service = endpoint.serviceName();
      this.ipv4 = endpoint.ipv4() == null ? null : InetAddresses.forString(endpoint.ipv4());
      this.ipv6 = endpoint.ipv6() == null ? null : InetAddresses.forString(endpoint.ipv6());
      this.port = endpoint.portAsInt();
    }

    public String getService() {
      return service;
    }

    public InetAddress getIpv4() {
      return ipv4;
    }

    public InetAddress getIpv6() {
      return ipv6;
    }

    public int getPort() {
      return port;
    }

    public void setService(String service) {
      this.service = service;
    }

    public void setIpv4(InetAddress ipv4) {
      this.ipv4 = ipv4;
    }

    public void setIpv6(InetAddress ipv6) {
      this.ipv6 = ipv6;
    }

    public void setPort(int port) {
      this.port = port;
    }

    Endpoint toEndpoint() {
      Endpoint.Builder builder = Endpoint.newBuilder().serviceName(service).port(port);
      builder.parseIp(ipv4);
      builder.parseIp(ipv6);
      return builder.build();
    }
  }

  @UDT(name = "annotation")
  static final class AnnotationUDT implements Serializable { // for Spark jobs
    private static final long serialVersionUID = 0L;

    private long ts;
    private String v;

    AnnotationUDT() {
      this.ts = 0;
      this.v = null;
    }

    AnnotationUDT(Annotation annotation) {
      this.ts = annotation.timestamp();
      this.v = annotation.value();
    }

    public long getTs() {
      return ts;
    }

    public String getV() {
      return v;
    }

    public void setTs(long ts) {
      this.ts = ts;
    }

    public void setV(String v) {
      this.v = v;
    }

    Annotation toAnnotation() {
      return Annotation.create(ts, v);
    }
  }
}
