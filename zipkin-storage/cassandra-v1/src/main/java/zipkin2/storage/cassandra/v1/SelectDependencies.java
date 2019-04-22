/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.internal.Dependencies;
import zipkin2.internal.DependencyLinker;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.internal.DateUtil.getDays;

final class SelectDependencies extends ResultSetFutureCall<List<DependencyLink>> {
  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(Session session) {
      this.session = session;
      Select.Where select =
          QueryBuilder.select("dependencies")
              .from("dependencies")
              .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days")));
      this.preparedStatement = session.prepare(select);
    }

    Call<List<DependencyLink>> create(long endTs, long lookback) {
      List<Date> days = getDays(endTs, lookback);
      return new SelectDependencies(this, days);
    }
  }

  final Factory factory;
  final List<Date> days;

  SelectDependencies(Factory factory, List<Date> days) {
    this.factory = factory;
    this.days = days;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind().setList("days", days));
  }

  @Override
  public String toString() {
    return "SelectDependencies{days=" + days + "}";
  }

  @Override
  public SelectDependencies clone() {
    return new SelectDependencies(factory, days);
  }

  @Override
  public List<DependencyLink> map(ResultSet rs) {
    List<DependencyLink> unmerged = new ArrayList<>();
    for (Row row : rs) {
      ByteBuffer encodedDayOfDependencies = row.getBytes("dependencies");
      unmerged.addAll(Dependencies.fromThrift(encodedDayOfDependencies).links());
    }
    return DependencyLinker.merge(unmerged);
  }
}
