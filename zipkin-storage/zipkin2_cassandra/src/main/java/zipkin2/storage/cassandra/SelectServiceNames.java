/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_SPANS;

final class SelectServiceNames extends ResultSetFutureCall {
  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final AccumulateServicesAllResults accumulateServicesIntoSet =
      new AccumulateServicesAllResults();

    Factory(Session session) {
      this.session = session;
      this.preparedStatement = session.prepare(
        QueryBuilder.select("service").distinct().from(TABLE_SERVICE_SPANS)
      );
    }

    Call<List<String>> create() {
      return new SelectServiceNames(this).flatMap(accumulateServicesIntoSet);
    }
  }

  final Factory factory;

  SelectServiceNames(Factory factory) {
    this.factory = factory;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind());
  }

  @Override public String toString() {
    return "SelectServiceNames{}";
  }

  @Override public SelectServiceNames clone() {
    return new SelectServiceNames(factory);
  }

  static class AccumulateServicesAllResults extends AccumulateAllResults<List<String>> {
    @Override protected Supplier<List<String>> supplier() {
      return ArrayList::new; // list is ok because it is distinct results
    }

    @Override protected BiConsumer<Row, List<String>> accumulator() {
      return (row, list) -> list.add(row.getString("service"));
    }

    @Override public String toString() {
      return "AccumulateServicesAllResults{}";
    }
  }
}
