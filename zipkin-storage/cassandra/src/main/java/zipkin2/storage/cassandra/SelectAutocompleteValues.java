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

import static zipkin2.storage.cassandra.Schema.TABLE_AUTOCOMPLETE_TAGS;

final class SelectAutocompleteValues extends ResultSetFutureCall<AsyncResultSet> {
  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CqlSession session) {
      this.session = session;
      this.preparedStatement = session.prepare("SELECT value"
        + " FROM " + TABLE_AUTOCOMPLETE_TAGS
        + " WHERE key=?"
        + " LIMIT " + 10000);
    }

    Call<List<String>> create(String key) {
      return new SelectAutocompleteValues(this, key).flatMap(DistinctSortedStrings.get());
    }
  }

  final SelectAutocompleteValues.Factory factory;
  final String key;

  SelectAutocompleteValues(SelectAutocompleteValues.Factory factory, String key) {
    this.factory = factory;
    this.key = key;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.boundStatementBuilder()
      .setString(0, key).build());
  }

  @Override public AsyncResultSet map(AsyncResultSet input) {
    return input;
  }

  @Override public Call<AsyncResultSet> clone() {
    return new SelectAutocompleteValues(factory, key);
  }
}
