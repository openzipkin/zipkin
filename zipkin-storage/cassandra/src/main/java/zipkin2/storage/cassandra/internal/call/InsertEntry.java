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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.Map.Entry;
import zipkin2.Call;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;

public final class InsertEntry extends DeduplicatingInsert<Entry<String, String>> {
  public static class Factory extends DeduplicatingInsert.Factory<Entry<String, String>> {
    final Session session;
    final String table, keyColumn, valueColumn;
    final PreparedStatement preparedStatement;

    public Factory(String table, String keyColumn, String valueColumn,
      Session session, long ttl, int cardinality) {
      this(table, keyColumn, valueColumn, session, ttl, cardinality, 0);
    }

    /** Cassandra v1 has deprecated support for indexTtl. */
    public Factory(String table, String keyColumn, String valueColumn,
      Session session, long ttl, int cardinality, int indexTtl) {
      super(ttl, cardinality);
      this.session = session;
      this.table = table;
      this.keyColumn = keyColumn;
      this.valueColumn = valueColumn;
      Insert insertQuery = insertInto(table)
        .value(keyColumn, bindMarker())
        .value(valueColumn, bindMarker());
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));

      preparedStatement = prepare(session, insertQuery);
    }

    protected PreparedStatement prepare(Session session, Insert insertQuery) {
      return session.prepare(insertQuery);
    }

    @Override protected Call<Void> newCall(Entry<String, String> input) {
      return new InsertEntry(this, input);
    }
  }

  final Factory factory;

  InsertEntry(Factory factory, Entry<String, String> input) {
    super(factory.delayLimiter, input);
    this.factory = factory;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString(0, input.getKey())
      .setString(1, input.getValue()));
  }

  @Override public String toString() {
    return "InsertEntry{table=" + factory.table + ", "
      + factory.keyColumn + "=" + input.getKey() + ", "
      + factory.valueColumn + "=" + input.getValue()
      + "}";
  }

  @Override public Call<Void> clone() {
    return new InsertEntry(factory, input);
  }
}
