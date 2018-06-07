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
import com.google.auto.value.AutoValue;

final class InsertSpanName extends DeduplicatingCall<InsertSpanName.Input> {

  @AutoValue
  abstract static class Input {
    abstract String service_name();

    abstract String span_name();

    Input() {}
  }

  static class Factory extends DeduplicatingCall.Factory<Input, InsertSpanName> {
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
          QueryBuilder.insertInto(Tables.SPAN_NAMES)
              .value("service_name", QueryBuilder.bindMarker("service_name"))
              .value("bucket", 0) // bucket is deprecated on this index
              .value("span_name", QueryBuilder.bindMarker("span_name"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));

      this.preparedStatement = session.prepare(insertQuery);
    }

    Input newInput(String service_name, String span_name) {
      return new AutoValue_InsertSpanName_Input(service_name, span_name);
    }

    @Override
    InsertSpanName newCall(Input input) {
      return new InsertSpanName(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertSpanName(Factory factory, Input input) {
    super(factory, input);
    this.factory = factory;
    this.input = input;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
        factory
            .preparedStatement
            .bind()
            .setString("service_name", input.service_name())
            .setString("span_name", input.span_name()));
  }

  @Override
  public String toString() {
    return input.toString().replace("Input", "InsertSpanName");
  }

  @Override
  public InsertSpanName clone() {
    return new InsertSpanName(factory, input);
  }
}
