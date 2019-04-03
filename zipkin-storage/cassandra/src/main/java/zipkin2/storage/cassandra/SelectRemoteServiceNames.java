/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.google.common.base.Preconditions.checkNotNull;
import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_REMOTE_SERVICES;

final class SelectRemoteServiceNames extends ResultSetFutureCall<ResultSet> {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final AccumulateNamesAllResults accumulateNamesIntoSet = new AccumulateNamesAllResults();

    Factory(Session session) {
      this.session = session;
      this.preparedStatement =
          session.prepare(
              QueryBuilder.select("remote_service")
                  .from(TABLE_SERVICE_REMOTE_SERVICES)
                  .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
                  .limit(QueryBuilder.bindMarker("limit_")));
    }

    Call<List<String>> create(String serviceName) {
      if (serviceName == null || serviceName.isEmpty()) return Call.emptyList();
      String service = checkNotNull(serviceName, "serviceName").toLowerCase();
      return new SelectRemoteServiceNames(this, service).flatMap(accumulateNamesIntoSet);
    }
  }

  final Factory factory;
  final String service;

  SelectRemoteServiceNames(Factory factory, String service) {
    this.factory = factory;
    this.service = service;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement
      .bind()
      .setString("service", service)
      .setInt("limit_", 1000)); // no one is ever going to browse so many span names
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override
  public String toString() {
    return "SelectSpanNames{service=" + service + "}";
  }

  @Override
  public SelectRemoteServiceNames clone() {
    return new SelectRemoteServiceNames(factory, service);
  }

  static class AccumulateNamesAllResults extends AccumulateAllResults<List<String>> {
    @Override
    protected Supplier<List<String>> supplier() {
      return ArrayList::new; // TODO: list might not be ok due to not distinct
    }

    @Override
    protected BiConsumer<Row, List<String>> accumulator() {
      return (row, list) -> {
        String result = row.getString("remote_service");
        if (!result.isEmpty()) list.add(result);
      };
    }

    @Override
    public String toString() {
      return "AccumulateNamesAllResults{}";
    }
  }
}
