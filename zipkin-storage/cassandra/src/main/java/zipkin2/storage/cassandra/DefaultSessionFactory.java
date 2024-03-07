/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import zipkin2.storage.cassandra.internal.SessionBuilder;

final class DefaultSessionFactory implements CassandraStorage.SessionFactory {
  @Override public CqlSession create(CassandraStorage cassandra) {
    return buildSession(cassandra);
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
}
