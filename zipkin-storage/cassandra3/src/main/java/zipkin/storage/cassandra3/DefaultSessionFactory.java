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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.mapping.MappingManager;
import com.github.jkutner.EnvKeyStore;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.storage.cassandra3.Schema.AnnotationUDT;
import zipkin.storage.cassandra3.Schema.BinaryAnnotationUDT;
import zipkin.storage.cassandra3.Schema.EndpointUDT;
import zipkin.storage.cassandra3.Schema.TypeCodecImpl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static zipkin.storage.cassandra3.Schema.DEFAULT_KEYSPACE;

/**
 * Creates a session and ensures schema if configured. Closes the cluster and session if any
 * exception occurred.
 */
final class DefaultSessionFactory implements Cassandra3Storage.SessionFactory {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultSessionFactory.class);

  /**
   * Creates a session and ensures schema if configured. Closes the cluster and session if any
   * exception occurred.
   */
  @Override public Session create(Cassandra3Storage cassandra) {
    Closer closer = Closer.create();
    try {
      Cluster cluster = closer.register(buildCluster(cassandra));
      cluster.register(new QueryLogger.Builder().build());
      Session session;
      if (cassandra.ensureSchema) {
        session = closer.register(cluster.connect());
        Schema.ensureExists(cassandra.keyspace, session);
        session.execute("USE " + cassandra.keyspace);
      } else {
        session = cluster.connect(cassandra.keyspace);
      }

      initializeUDTs(session);

      return session;
    } catch (RuntimeException e) {
      try {
        closer.close();
      } catch (IOException ignored) {
      }
      throw e;
    }
  }

  private static void initializeUDTs(Session session) {
    Schema.ensureExists(DEFAULT_KEYSPACE + "_udts", session);
    MappingManager mapping = new MappingManager(session);
    TypeCodec<AnnotationUDT> annoCodec = mapping.udtCodec(AnnotationUDT.class);
    TypeCodec<BinaryAnnotationUDT> bAnnoCodec = mapping.udtCodec(BinaryAnnotationUDT.class);

    // The UDTs are hardcoded against the zipkin keyspace.
    // If a different keyspace is being used the codecs must be re-applied to this different keyspace
    TypeCodec<EndpointUDT> endpointCodec = mapping.udtCodec(EndpointUDT.class);
    KeyspaceMetadata keyspace =
        session.getCluster().getMetadata().getKeyspace(session.getLoggedKeyspace());

    session.getCluster().getConfiguration().getCodecRegistry()
        .register(
            new TypeCodecImpl(keyspace.getUserType("endpoint"), EndpointUDT.class, endpointCodec))
        .register(
            new TypeCodecImpl(keyspace.getUserType("annotation"), AnnotationUDT.class, annoCodec))
        .register(
            new TypeCodecImpl(keyspace.getUserType("binary_annotation"), BinaryAnnotationUDT.class,
                bAnnoCodec));
  }

  // Visible for testing
  static Cluster buildCluster(Cassandra3Storage cassandra) {
    LOG.error("building cassandra3 cluster");

    Cluster.Builder builder = Cluster.builder();
    List<InetSocketAddress> contactPoints = parseContactPoints(cassandra);
    int defaultPort = findConnectPort(contactPoints);
    builder.addContactPointsWithPorts(contactPoints);
    builder.withPort(defaultPort); // This ends up protocolOptions.port
    if (cassandra.username != null && cassandra.password != null) {
      builder.withCredentials(cassandra.username, cassandra.password);
    }
    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE);
    builder.withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(
        cassandra.localDc != null
            ? DCAwareRoundRobinPolicy.builder().withLocalDc(cassandra.localDc).build()
            : new RoundRobinPolicy()
        // This can select remote, but LatencyAwarePolicy will prefer local
    ).build()));
    builder.withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(
        HostDistance.LOCAL, cassandra.maxConnections
    ));
    // https://github.com/datastax/java-driver/tree/3.x/manual/ssl#driver-configuration
    LOG.error("building cassandra3 ssl options");
    SSLOptions sslOptions = buildSSLOptions();
    LOG.error("built cassandra3 ssl options; {}", sslOptions);
    return builder.withSSL(sslOptions).build();
  }

  private static SSLOptions buildSSLOptions() {
    try {
      KeyStore ts = EnvKeyStore.create("CASSANDRA_TRUSTED_CERT").keyStore();
      String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
      tmf.init(ts);

      LOG.error("using keystore; {}", ts);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

      return JdkSSLOptions.builder().withSSLContext(sslContext).build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static List<InetSocketAddress> parseContactPoints(Cassandra3Storage cassandra) {
    List<InetSocketAddress> result = new LinkedList<>();
    for (String contactPoint : cassandra.contactPoints.split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      result.add(
          new InetSocketAddress(parsed.getHostText(), parsed.getPortOrDefault(9042)));
    }
    return result;
  }

  /** Returns the consistent port across all contact points or 9042 */
  static int findConnectPort(List<InetSocketAddress> contactPoints) {
    Set<Integer> ports = Sets.newLinkedHashSet();
    for (InetSocketAddress contactPoint : contactPoints) {
      ports.add(contactPoint.getPort());
    }
    return ports.size() == 1 ? ports.iterator().next() : 9042;
  }
}
