/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.exceptions.InvalidTypeException;
import com.datastax.driver.mapping.annotations.UDT;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Annotation;
import zipkin2.Endpoint;


final class Schema {
  private static final Logger LOG = LoggerFactory.getLogger(Schema.class);
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  static final String TABLE_SPAN = "span";
  static final String TABLE_TRACE_BY_SERVICE_SPAN = "trace_by_service_span";
  static final String TABLE_SERVICE_SPANS = "span_by_service";
  static final String TABLE_DEPENDENCY = "dependency";

  static final String DEFAULT_KEYSPACE = "zipkin2";
  private static final String SCHEMA_RESOURCE = "/zipkin2-schema.cql";
  private static final String INDEX_RESOURCE = "/zipkin2-schema-indexes.cql";

  private Schema() {
  }

  static Metadata readMetadata(Session session) {
    KeyspaceMetadata keyspaceMetadata = getKeyspaceMetadata(session);

    Map<String, String> replication = keyspaceMetadata.getReplication();
    if ("SimpleStrategy".equals(replication.get("class"))) {
      if ("1".equals(replication.get("replication_factor"))) {
        LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
      }

      ConsistencyLevel cl =
              session.getCluster().getConfiguration().getQueryOptions().getConsistencyLevel();

      Preconditions.checkState(
              ConsistencyLevel.ONE == cl,
              "Do not define `local_dc` and use SimpleStrategy");
    }
    String compactionClass =
        keyspaceMetadata.getTable("span").getOptions().getCompaction().get("class");

    return new Metadata(compactionClass);
  }

  static final class Metadata {
    final String compactionClass;

    Metadata(String compactionClass) {
      this.compactionClass = compactionClass;
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

  static KeyspaceMetadata ensureExists(String keyspace, boolean searchEnabled, Session session) {
    KeyspaceMetadata result = session.getCluster().getMetadata().getKeyspace(keyspace);
    if (result == null || result.getTable(Schema.TABLE_SPAN) == null) {
      LOG.info("Installing schema {}", SCHEMA_RESOURCE);
      applyCqlFile(keyspace, session, SCHEMA_RESOURCE);
      if (searchEnabled) {
        LOG.info("Installing indexes {}", INDEX_RESOURCE);
        applyCqlFile(keyspace, session, INDEX_RESOURCE);
      }
      // refresh metadata since we've installed the schema
      result = session.getCluster().getMetadata().getKeyspace(keyspace);
    }
    return result;
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

  @UDT(keyspace = DEFAULT_KEYSPACE + "_udts", name = "endpoint")
  static final class EndpointUDT implements Serializable { // for Spark jobs
    private static final long serialVersionUID = 0L;

    private String service;
    private InetAddress ipv4;
    private InetAddress ipv6;
    private Integer port;

    EndpointUDT() {
      this.service = null;
      this.ipv4 = null;
      this.ipv6 = null;
      this.port = null;
    }

    EndpointUDT(Endpoint endpoint) {
      this.service = endpoint.serviceName();
      this.ipv4 = endpoint.ipv4() == null ? null : InetAddresses.forString(endpoint.ipv4());
      this.ipv6 = endpoint.ipv6() == null ? null : InetAddresses.forString(endpoint.ipv6());
      this.port = endpoint.port();
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

    public Integer getPort() {
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

  @UDT(keyspace = DEFAULT_KEYSPACE + "_udts", name = "annotation")
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

  static final class TypeCodecImpl<T> extends TypeCodec<T> {

    private final TypeCodec<T> codec;

    public TypeCodecImpl(DataType cqlType, Class<T> javaClass, TypeCodec<T> codec) {
      super(cqlType, javaClass);
      this.codec = codec;
    }

    @Override
    public ByteBuffer serialize(T t, ProtocolVersion pv) throws InvalidTypeException {
      return codec.serialize(t, pv);
    }

    @Override
    public T deserialize(ByteBuffer bb, ProtocolVersion pv) throws InvalidTypeException {
      return codec.deserialize(bb, pv);
    }

    @Override
    public T parse(String string) throws InvalidTypeException {
      return codec.parse(string);
    }

    @Override
    public String format(T t) throws InvalidTypeException {
      return codec.format(t);
    }
  }
}
