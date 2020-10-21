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
package zipkin2.storage.cassandra.v1;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DistinctSortedStrings;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.v1.Tables.REMOTE_SERVICE_NAMES;

final class SelectRemoteServiceNames extends ResultSetFutureCall<AsyncResultSet> {
  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;

    Factory(CqlSession session) {
      this.session = session;
      this.preparedStatement = session.prepare("SELECT remote_service_name"
        + " FROM " + REMOTE_SERVICE_NAMES
        + " WHERE service_name=?"
        + " LIMIT " + 1000);
    }

    Call<List<String>> create(String serviceName) {
      if (serviceName == null || serviceName.isEmpty()) return Call.emptyList();
      String service = serviceName.toLowerCase(Locale.ROOT); // service names are always lowercase!
      return new SelectRemoteServiceNames(this, service).flatMap(DistinctSortedStrings.get());
    }
  }

  final Factory factory;
  final String service_name;

  SelectRemoteServiceNames(Factory factory, String service_name) {
    this.factory = factory;
    this.service_name = service_name;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.boundStatementBuilder()
      .setString(0, service_name).build());
  }

  @Override public AsyncResultSet map(AsyncResultSet input) {
    return input;
  }

  @Override public String toString() {
    return "SelectRemoteServiceNames{service_name=" + service_name + "}";
  }

  @Override public SelectRemoteServiceNames clone() {
    return new SelectRemoteServiceNames(factory, service_name);
  }
}
