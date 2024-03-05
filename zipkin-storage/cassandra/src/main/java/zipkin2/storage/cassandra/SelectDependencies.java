/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_DEPENDENCY;

final class SelectDependencies extends ResultSetFutureCall<List<DependencyLink>> {
  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CqlSession session) {
      this.session = session;
      this.preparedStatement = session.prepare("SELECT parent,child,errors,calls"
        + " FROM " + TABLE_DEPENDENCY
        + " WHERE day IN ?");
    }

    Call<List<DependencyLink>> create(long endTs, long lookback) {
      List<LocalDate> days = CassandraUtil.getDays(endTs, lookback);
      return new SelectDependencies(this, days);
    }
  }

  final Factory factory;
  final List<LocalDate> days;

  SelectDependencies(Factory factory, List<LocalDate> days) {
    this.factory = factory;
    this.days = days;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.boundStatementBuilder()
      .setList(0, days, LocalDate.class).build());
  }

  @Override public String toString() {
    return "SelectDependencies{days=" + days + "}";
  }

  @Override public SelectDependencies clone() {
    return new SelectDependencies(factory, days);
  }

  @Override public List<DependencyLink> map(AsyncResultSet rs) {
    List<DependencyLink> unmerged = new ArrayList<>();
    for (Row row : rs.currentPage()) {
      unmerged.add(DependencyLink.newBuilder()
        .parent(row.getString("parent"))
        .child(row.getString("child"))
        .errorCount(row.getLong("errors"))
        .callCount(row.getLong("calls"))
        .build());
    }
    return DependencyLinker.merge(unmerged);
  }
}
