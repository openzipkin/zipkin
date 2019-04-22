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
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

final class InsertServiceName extends ResultSetFutureCall<Void> {

  static class Factory extends DeduplicatingVoidCallFactory<String> {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(CassandraStorage storage, int indexTtl) {
      super(storage.autocompleteTtl, storage.autocompleteCardinality);
      session = storage.session();
      Insert insertQuery = QueryBuilder.insertInto(Tables.SERVICE_NAMES)
        .value("service_name", QueryBuilder.bindMarker("service_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
    }

    @Override protected InsertServiceName newCall(String input) {
      return new InsertServiceName(this, input);
    }
  }

  final Factory factory;
  final String service_name;

  InsertServiceName(Factory factory, String service_name) {
    this.factory = factory;
    this.service_name = service_name;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
      factory.preparedStatement.bind().setString("service_name", service_name));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return "InsertServiceName(" + service_name + ")";
  }

  @Override public InsertServiceName clone() {
    return new InsertServiceName(factory, service_name);
  }
}
