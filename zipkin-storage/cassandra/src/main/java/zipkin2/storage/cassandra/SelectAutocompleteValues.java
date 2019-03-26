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

import static zipkin2.storage.cassandra.Schema.TABLE_AUTOCOMPLETE_TAGS;

final class SelectAutocompleteValues extends ResultSetFutureCall<ResultSet> {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final AccumulateAutocompleteValues accumulateAutocompleteValues;

    Factory(Session session) {
      this.session = session;
      this.preparedStatement = session.prepare(
        QueryBuilder.select("value")
          .from(TABLE_AUTOCOMPLETE_TAGS)
          .where(QueryBuilder.eq("key", QueryBuilder.bindMarker("key")))
          .limit(QueryBuilder.bindMarker("limit_")));
      this.accumulateAutocompleteValues = new AccumulateAutocompleteValues();
    }

    Call<List<String>> create(String key) {
      return new SelectAutocompleteValues(this, key).flatMap(accumulateAutocompleteValues);
    }
  }

  final SelectAutocompleteValues.Factory factory;
  final String key;

  SelectAutocompleteValues(SelectAutocompleteValues.Factory factory, String key) {
    this.factory = factory;
    this.key = key;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement
      .bind()
      .setString("key", key)
      .setInt("limit_", 1000)); // no one is ever going to browse so many tag values
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override public Call<ResultSet> clone() {
    return new SelectAutocompleteValues(factory, key);
  }

  static class AccumulateAutocompleteValues extends AccumulateAllResults<List<String>> {
    @Override protected Supplier<List<String>> supplier() {
      return ArrayList::new; // list is ok because it is distinct results
    }

    @Override protected BiConsumer<Row, List<String>> accumulator() {
      return (row, list) -> {
        String result = row.getString("value");
        if (!result.isEmpty()) list.add(result);
      };
    }

    @Override
    public String toString() {
      return "AccumulateAutocompleteValues{}";
    }
  }
}
