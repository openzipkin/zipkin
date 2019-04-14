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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.List;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DistinctSortedStrings;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.google.common.base.Preconditions.checkNotNull;

final class SelectRemoteServiceNames extends ResultSetFutureCall<ResultSet> {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final DistinctSortedStrings remoteServiceNames =
      new DistinctSortedStrings("remote_service_name");

    Factory(Session session) {
      this.session = session;
      this.preparedStatement = session.prepare(QueryBuilder.select("remote_service_name")
        .from(Tables.REMOTE_SERVICE_NAMES)
        .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
        .limit(QueryBuilder.bindMarker("limit_")));
    }

    Call<List<String>> create(String serviceName) {
      if (serviceName == null || serviceName.isEmpty()) return Call.emptyList();
      String service = checkNotNull(serviceName, "serviceName").toLowerCase();
      return new SelectRemoteServiceNames(this, service).flatMap(remoteServiceNames);
    }
  }

  final Factory factory;
  final String service_name;

  SelectRemoteServiceNames(Factory factory, String service_name) {
    this.factory = factory;
    this.service_name = service_name;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service_name", service_name)
      // no one is ever going to browse so many remote service names
      .setInt("limit_", 1000));
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override public String toString() {
    return "SelectRemoteServiceNames{service_name=" + service_name + "}";
  }

  @Override public SelectRemoteServiceNames clone() {
    return new SelectRemoteServiceNames(factory, service_name);
  }
}
