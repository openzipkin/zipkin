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
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.HexCodec;
import zipkin2.internal.Nullable;
import zipkin2.internal.V1ThriftSpanReader;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

final class SelectFromTraces extends ResultSetFutureCall {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final AccumulateSpansAllResults accumulateSpans;
    final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
    final int maxTraceCols; // amount of spans per trace is almost always larger than trace IDs
    final boolean strictTraceId;

    Factory(Session session, boolean strictTraceId, int maxTraceCols) {
      this.session = session;
      this.accumulateSpans = new AccumulateSpansAllResults();

      this.preparedStatement =
          session.prepare(
              QueryBuilder.select("trace_id", "span")
                  .from("traces")
                  .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
                  .limit(QueryBuilder.bindMarker("limit_")));
      this.maxTraceCols = maxTraceCols;
      this.strictTraceId = strictTraceId;
      this.groupByTraceId = new GroupByTraceId(strictTraceId);
    }

    Call<List<Span>> newCall(String hexTraceId) {
      long traceId = HexCodec.lowerHexToUnsignedLong(hexTraceId);
      Call<List<Span>> result =
          new SelectFromTraces(this, Collections.singleton(traceId), maxTraceCols)
              .flatMap(accumulateSpans);
      return strictTraceId ? result.map(new StrictTraceId(hexTraceId)) : result;
    }

    FlatMapper<Set<Long>, List<List<Span>>> newFlatMapper(QueryRequest request) {
      return new FlatMapTraceIdsToSelectFromSpans(request, strictTraceId);
    }

    class FlatMapTraceIdsToSelectFromSpans implements FlatMapper<Set<Long>, List<List<Span>>> {
      final int limit;
      @Nullable final FilterTraces filter;

      FlatMapTraceIdsToSelectFromSpans(QueryRequest request, boolean strictTraceId) {
        this.limit = request.limit();
        this.filter = strictTraceId ? new FilterTraces(request) : null;
      }

      @Override
      public String toString() {
        return "FlatMapTraceIdsToSelectFromSpans{limit=" + limit + "}";
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
            new SelectFromTraces(Factory.this, traceIds, maxTraceCols)
                .flatMap(accumulateSpans)
                .map(groupByTraceId);
        return filter != null ? result.map(filter) : result;
      }
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

  @Override
  public String toString() {
    return "SelectFromTraces{trace_id=" + trace_id + ", limit_=" + limit_ + "}";
  }

  @Override
  public SelectFromTraces clone() {
    return new SelectFromTraces(factory, trace_id, limit_);
  }

  static class AccumulateSpansAllResults extends AccumulateAllResults<List<Span>> {

    @Override
    protected Supplier<List<Span>> supplier() {
      return ArrayList::new;
    }

    @Override
    protected BiConsumer<Row, List<Span>> accumulator() {
      return (row, result) -> {
        V1ThriftSpanReader reader = V1ThriftSpanReader.create();
        V1SpanConverter converter = V1SpanConverter.create();
        V1Span read = reader.read(row.getBytes("span"));
        converter.convert(read, result);
      };
    }

    @Override
    public String toString() {
      return "AccumulateSpansAllResults{}";
    }
  }

  static final class StrictTraceId implements Call.Mapper<List<Span>, List<Span>> {
    final String traceId;

    StrictTraceId(String traceId) {
      this.traceId = traceId;
    }

    @Override
    public List<Span> map(List<Span> input) {
      if (input.isEmpty()) return Collections.emptyList();
      input.removeIf(span -> !span.traceId().equals(traceId));
      return input;
    }

    @Override
    public String toString() {
      return "StrictTraceId{" + traceId + "}";
    }
  }

  /**
   * Due to indexing being on the low 64 bits of the trace ID, our matches are imprecise on 128bit.
   * This means we have to do a client-side filter.
   */
  static class FilterTraces implements Call.Mapper<List<List<Span>>, List<List<Span>>> {
    final QueryRequest request;

    FilterTraces(QueryRequest request) {
      this.request = request;
    }

    @Override
    public List<List<Span>> map(List<List<Span>> input) {
      input.removeIf(next -> next.get(0).traceId().length() > 16 && !request.test(next));
      return input;
    }

    @Override
    public String toString() {
      return "FilterTraces{" + request + "}";
    }
  }

  // TODO(adriancole): at some later point we can refactor this out
  static class GroupByTraceId implements Call.Mapper<List<Span>, List<List<Span>>> {
    final boolean strictTraceId;

    GroupByTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
    }

    @Override
    public List<List<Span>> map(List<Span> input) {
      if (input.isEmpty()) return Collections.emptyList();

      Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
      for (Span span : input) {
        String traceId =
            strictTraceId || span.traceId().length() == 16
                ? span.traceId()
                : span.traceId().substring(16);
        if (!groupedByTraceId.containsKey(traceId)) {
          groupedByTraceId.put(traceId, new ArrayList<>());
        }
        groupedByTraceId.get(traceId).add(span);
      }
      return new ArrayList<>(groupedByTraceId.values());
    }

    @Override
    public String toString() {
      return "GroupByTraceId{strictTraceId=" + strictTraceId + "}";
    }
  }
}
