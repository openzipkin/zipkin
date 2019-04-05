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
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

final class InsertRemoteServiceName extends ResultSetFutureCall<Void> {

  @AutoValue
  abstract static class Input {
    abstract String service_name();

    abstract String remote_service_name();

    Input() {
    }
  }

  static class Factory extends DeduplicatingVoidCallFactory<Input> {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(CassandraStorage storage, int indexTtl) {
      super(storage.autocompleteTtl, storage.autocompleteCardinality);
      session = storage.session();
      Insert insertQuery = QueryBuilder.insertInto(Tables.REMOTE_SERVICE_NAMES)
        .value("service_name", QueryBuilder.bindMarker("service_name"))
        .value("remote_service_name", QueryBuilder.bindMarker("remote_service_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
    }

    Input newInput(String service_name, String remote_service_name) {
      return new AutoValue_InsertRemoteServiceName_Input(service_name, remote_service_name);
    }

    @Override protected InsertRemoteServiceName newCall(Input input) {
      return new InsertRemoteServiceName(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertRemoteServiceName(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service_name", input.service_name())
      .setString("remote_service_name", input.remote_service_name()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertSpanName");
  }

  @Override public InsertRemoteServiceName clone() {
    return new InsertRemoteServiceName(factory, input);
  }
}
