/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CompletionStage;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DistinctSortedStrings;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_SPANS;

final class SelectServiceNames extends ResultSetFutureCall<AsyncResultSet> {
  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CqlSession session) {
      this.session = session;
      this.preparedStatement = session.prepare("SELECT DISTINCT service"
        + " FROM " + TABLE_SERVICE_SPANS);
    }

    Call<List<String>> create() {
      return new SelectServiceNames(this).flatMap(DistinctSortedStrings.get());
    }
  }

  final Factory factory;

  SelectServiceNames(Factory factory) {
    this.factory = factory;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.bind());
  }

  @Override public AsyncResultSet map(AsyncResultSet input) {
    return input;
  }

  @Override public String toString() {
    return "SelectServiceNames{}";
  }

  @Override public SelectServiceNames clone() {
    return new SelectServiceNames(factory);
  }
}
