/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import com.datastax.driver.core.Session;
import java.util.List;
import java.util.Locale;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DistinctSortedStrings;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_REMOTE_SERVICES;

final class SelectRemoteServiceNames extends ResultSetFutureCall<ResultSet> {

  static final class Factory {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(Session session) {
      this.session = session;
      this.preparedStatement =
        session.prepare(select("remote_service").from(TABLE_SERVICE_REMOTE_SERVICES)
          .where(eq("service", bindMarker()))
          .limit(1000));
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

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind().setString(0, service));
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override public String toString() {
    return "SelectSpanNames{service=" + service + "}";
  }

  @Override public SelectRemoteServiceNames clone() {
    return new SelectRemoteServiceNames(factory, service);
  }
}
