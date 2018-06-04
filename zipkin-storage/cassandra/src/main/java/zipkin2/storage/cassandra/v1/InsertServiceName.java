/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;

final class InsertServiceName extends DeduplicatingCall<String> {

  static class Factory extends DeduplicatingCall.Factory<String, InsertServiceName> {
    final Session session;
    final PreparedStatement preparedStatement;

    /**
     * @param indexTtl how long cassandra will persist the rows
     * @param redundantCallTtl how long in milliseconds to obviate redundant calls
     */
    Factory(Session session, int indexTtl, int redundantCallTtl) {
      super(redundantCallTtl);
      this.session = session;
      Insert insertQuery =
          QueryBuilder.insertInto(Tables.SERVICE_NAMES)
              .value("service_name", QueryBuilder.bindMarker("service_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      this.preparedStatement = session.prepare(insertQuery);
    }

    @Override
    InsertServiceName newCall(String input) {
      return new InsertServiceName(this, input);
    }
  }

  final Factory factory;
  final String service_name;

  InsertServiceName(Factory factory, String service_name) {
    super(factory, service_name);
    this.factory = factory;
    this.service_name = service_name;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
        factory.preparedStatement.bind().setString("service_name", service_name));
  }

  @Override
  public String toString() {
    return "InsertServiceName(" + service_name + ")";
  }

  @Override
  public InsertServiceName clone() {
    return new InsertServiceName(factory, service_name);
  }
}
