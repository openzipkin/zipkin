/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.MutableCodecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.storage.cassandra.internal.SessionBuilder;

import static zipkin2.Call.propagateIfFatal;

/**
 * Creates a session and ensures schema if configured. Closes the cluster and session if any
 * exception occurred.
 */
final class DefaultSessionFactory implements CassandraStorage.SessionFactory {
  static final Logger LOG = LoggerFactory.getLogger(DefaultSessionFactory.class);

  /**
   * Creates a session and ensures schema if configured. Closes the cluster and session if any
   * exception occurred.
   */
  @Override public CqlSession create(CassandraStorage cassandra) {
    CqlSession session = null;
    try {
      session = buildSession(cassandra);

      String keyspace = cassandra.keyspace;
      if (cassandra.ensureSchema) {
        Schema.ensureExists(keyspace, cassandra.searchEnabled, session);
      } else {
        LOG.debug("Skipping schema check on keyspace {} as ensureSchema was false", keyspace);
      }

      session.execute("USE " + keyspace);
      initializeUDTs(session, keyspace);

      return session;
    } catch (RuntimeException | Error e) { // don't leak on unexpected exception!
      propagateIfFatal(e);
      if (session != null) session.close();
      throw e;
    }
  }

  static CqlSession buildSession(CassandraStorage cassandra) {
    return SessionBuilder.buildSession(
      cassandra.contactPoints,
      cassandra.localDc,
      cassandra.poolingOptions,
      cassandra.authProvider,
      cassandra.useSsl,
      cassandra.sslHostnameValidation
    );
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
}
