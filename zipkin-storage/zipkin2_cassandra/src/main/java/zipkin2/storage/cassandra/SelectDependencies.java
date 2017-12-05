/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.List;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_DEPENDENCY;

final class SelectDependencies extends ResultSetFutureCall {
  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(Session session) {
      this.session = session;
      this.preparedStatement = session.prepare(
        QueryBuilder.select("parent", "child", "errors", "calls")
          .from(TABLE_DEPENDENCY)
          .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days")))
      );
    }

    Call<List<DependencyLink>> create(long endTs, long lookback) {
      List<LocalDate> days = CassandraUtil.getDays(endTs, lookback);
      return new SelectDependencies(this, days).map(ConvertDependenciesResponse.INSTANCE);
    }
  }

  final Factory factory;
  final List<LocalDate> days;

  SelectDependencies(Factory factory, List<LocalDate> days) {
    this.factory = factory;
    this.days = days;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setList("days", days));
  }

  @Override public String toString() {
    return "SelectDependencies{days=" + days + "}";
  }

  @Override public SelectDependencies clone() {
    return new SelectDependencies(factory, days);
  }

  enum ConvertDependenciesResponse implements Mapper<ResultSet, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> map(ResultSet rs) {
      List<DependencyLink> unmerged = new ArrayList<>();
      for (Row row : rs) {
        unmerged.add(DependencyLink.newBuilder()
          .parent(row.getString("parent"))
          .child(row.getString("child"))
          .errorCount(row.getLong("errors"))
          .callCount(row.getLong("calls"))
          .build());
      }
      return DependencyLinker.merge(unmerged);
    }

    @Override public String toString() {
      return "MergeDependencies";
    }
  }
}
