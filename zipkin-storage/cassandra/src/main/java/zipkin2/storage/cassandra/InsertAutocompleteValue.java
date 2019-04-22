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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.Map;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_AUTOCOMPLETE_TAGS;

final class InsertAutocompleteValue extends ResultSetFutureCall<Void> {

  static class Factory extends DeduplicatingVoidCallFactory<Map.Entry<String, String>> {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(CassandraStorage storage) {
      super(storage.autocompleteTtl(), storage.autocompleteCardinality());
      session = storage.session();
      Insert insertQuery = QueryBuilder.insertInto(TABLE_AUTOCOMPLETE_TAGS)
        .value("key", QueryBuilder.bindMarker("key"))
        .value("value", QueryBuilder.bindMarker("value"));
      preparedStatement = session.prepare(insertQuery);
    }

    @Override protected Call<Void> newCall(Map.Entry<String, String> input) {
      return new InsertAutocompleteValue(this, input);
    }
  }

  final Factory factory;
  final Map.Entry<String, String> input;

  InsertAutocompleteValue(Factory factory, Map.Entry<String, String> input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("key", input.getKey())
      .setString("value", input.getValue()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return "InsertAutocompleteValue(" + input + ")";
  }

  @Override public Call<Void> clone() {
    return new InsertAutocompleteValue(factory, input);
  }
}
