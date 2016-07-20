/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record5;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOffsetStep;
import org.jooq.Table;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.DependencyLinkSpan;
import zipkin.internal.DependencyLinker;
import zipkin.internal.Lazy;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;
import zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static zipkin.BinaryAnnotation.Type.STRING;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.getDays;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class MySQLSpanStore implements SpanStore {
  static final Field<?>[] ANNOTATION_FIELDS_WITHOUT_IPV6;

  static {
    ArrayList<Field<?>> list = new ArrayList(Arrays.asList(ZIPKIN_ANNOTATIONS.fields()));
    list.remove(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6);
    list.trimToSize();
    ANNOTATION_FIELDS_WITHOUT_IPV6 = list.toArray(new Field<?>[list.size()]);
  }

  private final DataSource datasource;
  private final DSLContexts context;
  private final Lazy<Boolean> hasIpv6;
  private final Lazy<Boolean> hasPreAggregatedDependencies;

  MySQLSpanStore(DataSource datasource, DSLContexts context, Lazy<Boolean> hasIpv6,
      Lazy<Boolean> hasPreAggregatedDependencies) {
    this.datasource = datasource;
    this.context = context;
    this.hasIpv6 = hasIpv6;
    this.hasPreAggregatedDependencies = hasPreAggregatedDependencies;
  }

  private Endpoint endpoint(Record a) {
    String serviceName = a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    if (serviceName == null) return null;
    return Endpoint.builder()
        .serviceName(serviceName)
        .port(a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT))
        .ipv4(a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4))
        .ipv6(hasIpv6.get() ? a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6) : null).build();
  }

  private static SelectOffsetStep<Record1<Long>> toTraceIdQuery(DSLContext context,
      QueryRequest request) {
    long endTs = (request.endTs > 0 && request.endTs != Long.MAX_VALUE) ? request.endTs * 1000
        : System.currentTimeMillis() * 1000;

    Table<?> table = ZIPKIN_SPANS.join(ZIPKIN_ANNOTATIONS)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID).and(
            ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID)));

    Map<String, ZipkinAnnotations> keyToTables = new LinkedHashMap<>();
    int i = 0;
    for (String key : request.binaryAnnotations.keySet()) {
      keyToTables.put(key, ZIPKIN_ANNOTATIONS.as("a" + i++));
      table = join(table, keyToTables.get(key), key, STRING.value);
    }

    for (String key : request.annotations) {
      keyToTables.put(key, ZIPKIN_ANNOTATIONS.as("a" + i++));
      table = join(table, keyToTables.get(key), key, -1);
    }

    SelectConditionStep<Record1<Long>> dsl = context.selectDistinct(ZIPKIN_SPANS.TRACE_ID)
        .from(table)
        .where(ZIPKIN_SPANS.START_TS.between(endTs - request.lookback * 1000, endTs));

    if (request.serviceName != null) {
      dsl.and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(request.serviceName));
    }

    if (request.spanName != null) {
      dsl.and(ZIPKIN_SPANS.NAME.eq(request.spanName));
    }

    if (request.minDuration != null && request.maxDuration != null) {
      dsl.and(ZIPKIN_SPANS.DURATION.between(request.minDuration, request.maxDuration));
    } else if (request.minDuration != null) {
      dsl.and(ZIPKIN_SPANS.DURATION.greaterOrEqual(request.minDuration));
    }

    for (Map.Entry<String, String> entry : request.binaryAnnotations.entrySet()) {
      dsl.and(keyToTables.get(entry.getKey()).A_VALUE.eq(entry.getValue().getBytes(UTF_8)));
    }
    return dsl.orderBy(ZIPKIN_SPANS.START_TS.desc()).limit(request.limit);
  }

  static Table<?> join(Table<?> table, ZipkinAnnotations joinTable, String key, int type) {
    return table.join(joinTable)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(joinTable.TRACE_ID))
        .and(ZIPKIN_SPANS.ID.eq(joinTable.SPAN_ID))
        .and(joinTable.A_TYPE.eq(type))
        .and(joinTable.A_KEY.eq(key));
  }

  List<List<Span>> getTraces(@Nullable QueryRequest request, @Nullable Long traceId, boolean raw) {
    final Map<Long, List<Span>> spansWithoutAnnotations;
    final Map<Pair<?>, List<Record>> dbAnnotations;
    try (Connection conn = datasource.getConnection()) {
      Condition traceIdCondition;
      if (request != null) {
        List<Long> traceIds =
            toTraceIdQuery(context.get(conn), request).fetch(ZIPKIN_SPANS.TRACE_ID);
        traceIdCondition = ZIPKIN_SPANS.TRACE_ID.in(traceIds);
      } else {
        traceIdCondition = ZIPKIN_SPANS.TRACE_ID.eq(traceId);
      }
      spansWithoutAnnotations = context.get(conn)
          .selectFrom(ZIPKIN_SPANS).where(traceIdCondition)
          .stream()
          .map(r -> Span.builder()
              .traceId(r.getValue(ZIPKIN_SPANS.TRACE_ID))
              .name(r.getValue(ZIPKIN_SPANS.NAME))
              .id(r.getValue(ZIPKIN_SPANS.ID))
              .parentId(r.getValue(ZIPKIN_SPANS.PARENT_ID))
              .timestamp(r.getValue(ZIPKIN_SPANS.START_TS))
              .duration(r.getValue(ZIPKIN_SPANS.DURATION))
              .debug(r.getValue(ZIPKIN_SPANS.DEBUG))
              .build())
          .collect(
              groupingBy((Span s) -> s.traceId, LinkedHashMap::new, Collectors.<Span>toList()));

      dbAnnotations = context.get(conn)
          .select(hasIpv6.get() ? ZIPKIN_ANNOTATIONS.fields() : ANNOTATION_FIELDS_WITHOUT_IPV6)
          .from(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.TRACE_ID.in(spansWithoutAnnotations.keySet()))
          .orderBy(ZIPKIN_ANNOTATIONS.A_TIMESTAMP.asc(), ZIPKIN_ANNOTATIONS.A_KEY.asc())
          .stream()
          .collect(groupingBy((Record a) -> Pair.create(
              a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
              a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)
              ), LinkedHashMap::new,
              Collectors.<Record>toList())); // LinkedHashMap preserves order while grouping
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + request + ": " + e.getMessage());
    }

    List<List<Span>> result = new ArrayList<>(spansWithoutAnnotations.keySet().size());
    for (List<Span> spans : spansWithoutAnnotations.values()) {
      List<Span> trace = new ArrayList<>(spans.size());
      for (Span s : spans) {
        Span.Builder span = s.toBuilder();
        Pair<?> key = Pair.create(s.traceId, s.id);

        if (dbAnnotations.containsKey(key)) {
          for (Record a : dbAnnotations.get(key)) {
            Endpoint endpoint = endpoint(a);
            int type = a.getValue(ZIPKIN_ANNOTATIONS.A_TYPE);
            if (type == -1) {
              span.addAnnotation(Annotation.create(
                  a.getValue(ZIPKIN_ANNOTATIONS.A_TIMESTAMP),
                  a.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
                  endpoint));
            } else {
              span.addBinaryAnnotation(BinaryAnnotation.create(
                  a.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
                  a.getValue(ZIPKIN_ANNOTATIONS.A_VALUE),
                  Type.fromValue(type),
                  endpoint));
            }
          }
        }
        Span rawSpan = span.build();
        trace.add(raw ? rawSpan : ApplyTimestampAndDuration.apply(rawSpan));
      }
      if (!raw) trace = CorrectForClockSkew.apply(trace);
      result.add(trace);
    }
    if (!raw) Collections.sort(result, (left, right) -> right.get(0).compareTo(left.get(0)));
    return result;
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request) {
    return getTraces(request, null, false);
  }

  @Override
  public List<Span> getTrace(long traceId) {
    List<List<Span>> result = getTraces(null, traceId, false);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public List<Span> getRawTrace(long traceId) {
    List<List<Span>> result = getTraces(null, traceId, true);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public List<String> getServiceNames() {
    try (Connection conn = datasource.getConnection()) {
      return context.get(conn)
          .selectDistinct(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
          .from(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.isNotNull()
              .and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.ne("")))
          .fetch(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + e + ": " + e.getMessage());
    }
  }

  @Override
  public List<String> getSpanNames(String serviceName) {
    if (serviceName == null) return emptyList();
    serviceName = serviceName.toLowerCase(); // service names are always lowercase!
    try (Connection conn = datasource.getConnection()) {
      return context.get(conn)
          .selectDistinct(ZIPKIN_SPANS.NAME)
          .from(ZIPKIN_SPANS)
          .join(ZIPKIN_ANNOTATIONS)
          .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID))
          .and(ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID))
          .where(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(serviceName))
          .orderBy(ZIPKIN_SPANS.NAME)
          .fetch(ZIPKIN_SPANS.NAME);
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + serviceName + ": " + e.getMessage());
    }
  }

  @Override
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    try (Connection conn = datasource.getConnection()) {
      if (hasPreAggregatedDependencies.get()) {
        List<Date> days = getDays(endTs, lookback);
        List<DependencyLink> unmerged = context.get(conn)
            .selectFrom(ZIPKIN_DEPENDENCIES)
            .where(ZIPKIN_DEPENDENCIES.DAY.in(days))
            .fetch((Record l) -> DependencyLink.create(
                l.get(ZIPKIN_DEPENDENCIES.PARENT),
                l.get(ZIPKIN_DEPENDENCIES.CHILD),
                l.get(ZIPKIN_DEPENDENCIES.CALL_COUNT))
            );
        return DependencyLinker.merge(unmerged);
      } else {
        return aggregateDependencies(endTs, lookback, conn);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error querying dependencies for endTs " + endTs + " and lookback " + lookback + ": " + e.getMessage());
    }
  }

  List<DependencyLink> aggregateDependencies(long endTs, @Nullable Long lookback, Connection conn) {
    endTs = endTs * 1000;
    // Lazy fetching the cursor prevents us from buffering the whole dataset in memory.
    Cursor<Record5<Long, Long, Long, String, String>> cursor = context.get(conn)
        .selectDistinct(ZIPKIN_SPANS.TRACE_ID, ZIPKIN_SPANS.PARENT_ID, ZIPKIN_SPANS.ID,
            ZIPKIN_ANNOTATIONS.A_KEY, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
        // left joining allows us to keep a mapping of all span ids, not just ones that have
        // special annotations. We need all span ids to reconstruct the trace tree. We need
        // the whole trace tree so that we can accurately skip local spans.
        .from(ZIPKIN_SPANS.leftJoin(ZIPKIN_ANNOTATIONS)
            .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID).and(
                ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID)))
            .and(ZIPKIN_ANNOTATIONS.A_KEY.in(CLIENT_ADDR, SERVER_RECV, SERVER_ADDR)))
        .where(lookback == null ?
            ZIPKIN_SPANS.START_TS.lessOrEqual(endTs) :
            ZIPKIN_SPANS.START_TS.between(endTs - lookback * 1000, endTs))
        // Grouping so that later code knows when a span or trace is finished.
        .groupBy(ZIPKIN_SPANS.TRACE_ID, ZIPKIN_SPANS.ID, ZIPKIN_ANNOTATIONS.A_KEY).fetchLazy();

    Iterator<Iterator<DependencyLinkSpan>> traces =
        new DependencyLinkSpanIterator.ByTraceId(cursor.iterator());

    if (!traces.hasNext()) return Collections.emptyList();

    DependencyLinker linker = new DependencyLinker();

    while (traces.hasNext()) {
      linker.putTrace(traces.next());
    }

    return linker.link();
  }
}
