/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Call.FlatMapper;

public abstract class AccumulateAllResults<T> implements FlatMapper<AsyncResultSet, T> {
  protected abstract Supplier<T> supplier();

  protected abstract BiConsumer<Row, T> accumulator();

  /** Customizes the aggregated result. For example, summarizing or making immutable. */
  protected Function<T, T> finisher() {
    return Function.identity();
  }

  @Override public Call<T> map(AsyncResultSet rs) {
    return new AccumulateNextResults<>(
      supplier().get(),
      accumulator(),
      finisher()
    ).map(rs);
  }

  static final class FetchMoreResults extends ResultSetFutureCall<AsyncResultSet> {
    final AsyncResultSet resultSet;

    FetchMoreResults(AsyncResultSet resultSet) {
      this.resultSet = resultSet;
    }

    @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
      return resultSet.fetchNextPage().toCompletableFuture();
    }

    @Override public AsyncResultSet map(AsyncResultSet input) {
      return input;
    }

    @Override public Call<AsyncResultSet> clone() {
      throw new UnsupportedOperationException();
    }

    @Override public String toString() {
      return "FetchMoreResults{" + resultSet + "}";
    }
  }

  static final class AccumulateNextResults<T> implements FlatMapper<AsyncResultSet, T> {
    final T pendingResults;
    final BiConsumer<Row, T> accumulator;
    final Function<T, T> finisher;

    AccumulateNextResults(
      T pendingResults, BiConsumer<Row, T> accumulator, Function<T, T> finisher) {
      this.pendingResults = pendingResults;
      this.accumulator = accumulator;
      this.finisher = finisher;
    }

    /** Iterates through the rows in each page, flatmapping on more results until exhausted */
    @Override public Call<T> map(AsyncResultSet rs) {
      while (rs.remaining() > 0) {
        accumulator.accept(rs.one(), pendingResults);
      }
      // Return collected results if there are no more pages
      return rs.getExecutionInfo().getPagingState() == null && !rs.hasMorePages()
        ? Call.create(finisher.apply(pendingResults))
        : new FetchMoreResults(rs).flatMap(this);
    }
  }
}
