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
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.FilterTraces;
import zipkin2.internal.HexCodec;
import zipkin2.internal.Nullable;
import zipkin2.internal.ReadBuffer;
import zipkin2.internal.V1ThriftSpanReader;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.StrictTraceId;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

final class SelectFromTraces extends ResultSetFutureCall<ResultSet> {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final DecodeAndConvertSpans accumulateSpans;
    final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
    final int maxTraceCols; // amount of spans per trace is almost always larger than trace IDs
    final boolean strictTraceId;

    Factory(Session session, boolean strictTraceId, int maxTraceCols) {
      this.session = session;
      this.accumulateSpans = new DecodeAndConvertSpans();

      this.preparedStatement =
        session.prepare(
          QueryBuilder.select("trace_id", "span")
            .from("traces")
            .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
            .limit(QueryBuilder.bindMarker("limit_")));
      this.maxTraceCols = maxTraceCols;
      this.strictTraceId = strictTraceId;
      this.groupByTraceId = GroupByTraceId.create(strictTraceId);
    }

    Call<List<Span>> newCall(String hexTraceId) {
      long traceId = HexCodec.lowerHexToUnsignedLong(hexTraceId);
      Call<List<Span>> result =
        new SelectFromTraces(this, Collections.singleton(traceId), maxTraceCols)
          .flatMap(accumulateSpans);
      return strictTraceId ? result.map(StrictTraceId.filterSpans(hexTraceId)) : result;
    }

    FlatMapper<Set<Long>, List<List<Span>>> newFlatMapper(QueryRequest request) {
      return new SelectTracesByIds(this, request);
    }
  }

  final Factory factory;
  final Set<Long> trace_id;
  final int limit_;

  SelectFromTraces(Factory factory, Set<Long> trace_id, int limit_) {
    this.factory = factory;
    this.trace_id = trace_id;
    this.limit_ = limit_;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
      factory.preparedStatement.bind().setSet("trace_id", trace_id).setInt("limit_", limit_));
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override
  public String toString() {
    return "SelectFromTraces{trace_id=" + trace_id + ", limit_=" + limit_ + "}";
  }

  @Override
  public SelectFromTraces clone() {
    return new SelectFromTraces(factory, trace_id, limit_);
  }

  static final class SelectTracesByIds implements FlatMapper<Set<Long>, List<List<Span>>> {
    final Factory factory;
    final int limit;
    @Nullable final Call.Mapper<List<List<Span>>, List<List<Span>>> filter;

    SelectTracesByIds(Factory factory, QueryRequest request) {
      this.factory = factory;
      this.limit = request.limit();
      // Cassandra always looks up traces by 64-bit trace ID, so we have to unconditionally filter
      // when strict trace ID is enabled.
      this.filter = factory.strictTraceId ? FilterTraces.create(request) : null;
    }

    @Override
    public Call<List<List<Span>>> map(Set<Long> input) {
      if (input.isEmpty()) return Call.emptyList();
      Set<Long> traceIds;
      if (input.size() > limit) {
        traceIds = new LinkedHashSet<>();
        Iterator<Long> iterator = input.iterator();
        for (int i = 0; i < limit; i++) {
          traceIds.add(iterator.next());
        }
      } else {
        traceIds = input;
      }
      Call<List<List<Span>>> result =
        new SelectFromTraces(factory, traceIds, factory.maxTraceCols)
          .flatMap(factory.accumulateSpans)
          .map(factory.groupByTraceId);
      return filter != null ? result.map(filter) : result;
    }

    @Override
    public String toString() {
      return "SelectTracesByIds{limit=" + limit + "}";
    }
  }

  static final class DecodeAndConvertSpans extends AccumulateAllResults<List<Span>> {

    @Override
    protected Supplier<List<Span>> supplier() {
      return ArrayList::new;
    }

    @Override
    protected BiConsumer<Row, List<Span>> accumulator() {
      return (row, result) -> {
        V1ThriftSpanReader reader = V1ThriftSpanReader.create();
        V1SpanConverter converter = V1SpanConverter.create();
        V1Span read = reader.read(ReadBuffer.wrapUnsafe(row.getBytes("span")));
        converter.convert(read, result);
      };
    }

    @Override
    public String toString() {
      return "DecodeAndConvertSpans{}";
    }
  }
}
