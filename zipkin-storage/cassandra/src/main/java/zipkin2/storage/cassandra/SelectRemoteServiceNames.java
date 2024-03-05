/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DistinctSortedStrings;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_REMOTE_SERVICES;

final class SelectRemoteServiceNames extends ResultSetFutureCall<AsyncResultSet> {
  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CqlSession session) {
      this.session = session;
      this.preparedStatement = session.prepare("SELECT remote_service"
        + " FROM " + TABLE_SERVICE_REMOTE_SERVICES
        + " WHERE service=?"
        + " LIMIT " + 1000);
    }

    Call<List<String>> create(String serviceName) {
      if (serviceName == null || serviceName.isEmpty()) return Call.emptyList();
      String service = serviceName.toLowerCase(Locale.ROOT); // service names are always lowercase!
      return new SelectRemoteServiceNames(this, service).flatMap(DistinctSortedStrings.get());
    }
  }

  final Factory factory;
  final String service;

  SelectRemoteServiceNames(Factory factory, String service) {
    this.factory = factory;
    this.service = service;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.boundStatementBuilder()
      .setString(0, service).build());
  }

  @Override public AsyncResultSet map(AsyncResultSet input) {
    return input;
  }

  @Override public String toString() {
    return "SelectSpanNames{service=" + service + "}";
  }

  @Override public SelectRemoteServiceNames clone() {
    return new SelectRemoteServiceNames(factory, service);
  }
}
