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

package zipkin2.storage.cassandra;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.Map;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.DeduplicatingCall;

import static zipkin2.storage.cassandra.Schema.TABLE_AUTOCOMPLETE_TAGS;

final class InsertAutocompleteValue extends DeduplicatingCall<Map.Entry<String, String>> {

  static class Factory
    extends DeduplicatingCall.Factory<Map.Entry<String, String>, InsertAutocompleteValue> {
    final Session session;
    final PreparedStatement preparedStatement;

    /**
     * @param indexTtl how long cassandra will persist the rows
     * @param redundantCallTtl how long in milliseconds to obviate redundant calls
     */
    Factory(Session session, int indexTtl, int redundantCallTtl) {
      super(redundantCallTtl);
      this.session = session;
      Insert insertQuery = QueryBuilder.insertInto(TABLE_AUTOCOMPLETE_TAGS)
        .value("key", QueryBuilder.bindMarker("key"))
        .value("value", QueryBuilder.bindMarker("value"));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      this.preparedStatement = session.prepare(insertQuery);
    }

    @Override protected InsertAutocompleteValue newCall(Map.Entry<String, String> input) {
      return new InsertAutocompleteValue(this, input);
    }
  }

  final Factory factory;
  final Map.Entry<String, String> input;

  InsertAutocompleteValue(Factory factory, Map.Entry<String, String> input) {
    super(factory, input);
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("key", input.getKey())
      .setString("value", input.getValue()));
  }

  @Override public String toString() {
    return "InsertAutocompleteValue(" + input + ")";
  }

  @Override public Call<ResultSet> clone() {
    return new InsertAutocompleteValue(factory, input);
  }
}
