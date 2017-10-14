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
package zipkin.storage.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.jooq.Record;
import org.jooq.Row3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectField;
import org.jooq.SelectOffsetStep;
import org.jooq.TableField;
import org.jooq.TableOnConditionStep;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.internal.DependencyLinker;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStore;
import zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations;
import zipkin2.Span;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static org.jooq.impl.DSL.row;
import static zipkin.BinaryAnnotation.Type.STRING;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.ERROR;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.getDays;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class MySQLSpanStore implements SpanStore {

  private final DataSource datasource;
  private final DSLContexts context;
  private final Schema schema;
  private final boolean strictTraceId;

  MySQLSpanStore(DataSource datasource, DSLContexts context, Schema schema, boolean strictTraceId) {
    this.datasource = datasource;
    this.context = context;
    this.schema = schema;
    this.strictTraceId = strictTraceId;
  }

  private Endpoint endpoint(Record a) {
    String serviceName = a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    if (serviceName == null) return null;
    return Endpoint.builder()
        .serviceName(serviceName)
        .port(a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT))
        .ipv4(a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4))
        .ipv6(maybeGet(a, ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6, null)).build();
  }

  SelectOffsetStep<? extends Record> toTraceIdQuery(DSLContext context, QueryRequest request) {
    long endTs = (request.endTs > 0 && request.endTs != Long.MAX_VALUE) ? request.endTs * 1000
        : System.currentTimeMillis() * 1000;

    TableOnConditionStep<?> table = ZIPKIN_SPANS.join(ZIPKIN_ANNOTATIONS)
        .on(schema.joinCondition(ZIPKIN_ANNOTATIONS));

    int i = 0;
    for (String key : request.annotations) {
      ZipkinAnnotations aTable = ZIPKIN_ANNOTATIONS.as("a" + i++);
      table = maybeOnService(table.join(aTable)
          .on(schema.joinCondition(aTable))
          .and(aTable.A_KEY.eq(key)), aTable, request.serviceName);
    }

    for (Map.Entry<String, String> kv : request.binaryAnnotations.entrySet()) {
      ZipkinAnnotations aTable = ZIPKIN_ANNOTATIONS.as("a" + i++);
      table = maybeOnService(table.join(aTable)
          .on(schema.joinCondition(aTable))
          .and(aTable.A_TYPE.eq(STRING.value))
          .and(aTable.A_KEY.eq(kv.getKey()))
          .and(aTable.A_VALUE.eq(kv.getValue().getBytes(UTF_8))), aTable, request.serviceName);
    }

    List<SelectField<?>> distinctFields = new ArrayList<>(schema.spanIdFields);
    distinctFields.add(ZIPKIN_SPANS.START_TS.max());
    SelectConditionStep<Record> dsl = context.selectDistinct(distinctFields)
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
    return dsl
        .groupBy(schema.spanIdFields)
        .orderBy(ZIPKIN_SPANS.START_TS.max().desc()).limit(request.limit);
  }

  static TableOnConditionStep<?> maybeOnService(TableOnConditionStep<Record> table,
      ZipkinAnnotations aTable, String serviceName) {
    if (serviceName == null) return table;
    return table.and(aTable.ENDPOINT_SERVICE_NAME.eq(serviceName));
  }

  List<List<zipkin.Span>> getTraces(@Nullable QueryRequest request, @Nullable Long traceIdHigh,
      @Nullable Long traceIdLow, boolean raw) {
    if (traceIdHigh != null && !strictTraceId) traceIdHigh = null;
    final Map<Pair<Long>, List<zipkin.Span>> spansWithoutAnnotations;
    final Map<Row3<Long, Long, Long>, List<Record>> dbAnnotations;
    try (Connection conn = datasource.getConnection()) {
      Condition traceIdCondition = request != null
          ? schema.spanTraceIdCondition(toTraceIdQuery(context.get(conn), request))
          : schema.spanTraceIdCondition(traceIdHigh, traceIdLow);

      spansWithoutAnnotations = context.get(conn)
          .select(schema.spanFields)
          .from(ZIPKIN_SPANS).where(traceIdCondition)
          .stream()
          .map(r -> zipkin.Span.builder()
              .traceIdHigh(maybeGet(r, ZIPKIN_SPANS.TRACE_ID_HIGH, 0L))
              .traceId(r.getValue(ZIPKIN_SPANS.TRACE_ID))
              .name(r.getValue(ZIPKIN_SPANS.NAME))
              .id(r.getValue(ZIPKIN_SPANS.ID))
              .parentId(r.getValue(ZIPKIN_SPANS.PARENT_ID))
              .timestamp(r.getValue(ZIPKIN_SPANS.START_TS))
              .duration(r.getValue(ZIPKIN_SPANS.DURATION))
              .debug(r.getValue(ZIPKIN_SPANS.DEBUG))
              .build())
          .collect(
              groupingBy((zipkin.Span s) -> Pair.create(s.traceIdHigh, s.traceId),
                  LinkedHashMap::new, Collectors.<zipkin.Span>toList()));

      dbAnnotations = context.get(conn)
          .select(schema.annotationFields)
          .from(ZIPKIN_ANNOTATIONS)
          .where(schema.annotationsTraceIdCondition(spansWithoutAnnotations.keySet()))
          .orderBy(ZIPKIN_ANNOTATIONS.A_TIMESTAMP.asc(), ZIPKIN_ANNOTATIONS.A_KEY.asc())
          .stream()
          .collect(groupingBy((Record a) -> row(
              maybeGet(a, ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, 0L),
              a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
              a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)
              ), LinkedHashMap::new,
              Collectors.<Record>toList())); // LinkedHashMap preserves order while grouping
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + request + ": " + e.getMessage());
    }

    List<zipkin.Span> allSpans = new ArrayList<>(spansWithoutAnnotations.size());
    for (List<zipkin.Span> spans : spansWithoutAnnotations.values()) {
      for (zipkin.Span s : spans) {
        zipkin.Span.Builder span = s.toBuilder();
        Row3<Long, Long, Long> key = row(s.traceIdHigh, s.traceId, s.id);

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
        allSpans.add(span.build());
      }
    }
    return GroupByTraceId.apply(allSpans, strictTraceId, !raw);
  }

  static <T> T maybeGet(Record record, TableField<Record, T> field, T defaultValue) {
    if (record.fieldsRow().indexOf(field) < 0) {
      return defaultValue;
    } else {
      return record.get(field);
    }
  }

  @Override
  public List<List<zipkin.Span>> getTraces(QueryRequest request) {
    return getTraces(request, null, null, false);
  }

  @Override
  public List<zipkin.Span> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public List<zipkin.Span> getTrace(long traceIdHigh, long traceIdLow) {
    List<List<zipkin.Span>> result = getTraces(null, traceIdHigh, traceIdLow, false);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public List<zipkin.Span> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public List<zipkin.Span> getRawTrace(long traceIdHigh, long traceIdLow) {
    List<List<zipkin.Span>> result = getTraces(null, traceIdHigh, traceIdLow, true);
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
      if (schema.hasPreAggregatedDependencies) {
        List<Date> days = getDays(endTs, lookback);
        List<DependencyLink> unmerged = context.get(conn)
            .select(schema.dependencyLinkFields)
            .from(ZIPKIN_DEPENDENCIES)
            .where(ZIPKIN_DEPENDENCIES.DAY.in(days))
            .fetch((Record l) -> DependencyLink.builder()
                .parent(l.get(ZIPKIN_DEPENDENCIES.PARENT))
                .child(l.get(ZIPKIN_DEPENDENCIES.CHILD))
                .callCount(l.get(ZIPKIN_DEPENDENCIES.CALL_COUNT))
                .errorCount(maybeGet(l, ZIPKIN_DEPENDENCIES.ERROR_COUNT, 0L))
                .build()
            );
        return DependencyLinker.merge(unmerged);
      } else {
        return aggregateDependencies(endTs, lookback, conn);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error querying dependencies for endTs "
          + endTs + " and lookback " + lookback + ": " + e.getMessage());
    }
  }

  List<DependencyLink> aggregateDependencies(long endTs, @Nullable Long lookback, Connection conn) {
    endTs = endTs * 1000;
    // Lazy fetching the cursor prevents us from buffering the whole dataset in memory.
    Cursor<Record> cursor = context.get(conn)
        .selectDistinct(schema.dependencyLinkerFields)
        // left joining allows us to keep a mapping of all span ids, not just ones that have
        // special annotations. We need all span ids to reconstruct the trace tree. We need
        // the whole trace tree so that we can accurately skip local spans.
        .from(ZIPKIN_SPANS.leftJoin(ZIPKIN_ANNOTATIONS)
            // NOTE: we are intentionally grouping only on the low-bits of trace id. This buys time
            // for applications to upgrade to 128-bit instrumentation.
            .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID).and(
                ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID)))
            .and(ZIPKIN_ANNOTATIONS.A_KEY.in(CLIENT_SEND, CLIENT_ADDR, SERVER_RECV, SERVER_ADDR, ERROR)))
        .where(lookback == null ?
            ZIPKIN_SPANS.START_TS.lessOrEqual(endTs) :
            ZIPKIN_SPANS.START_TS.between(endTs - lookback * 1000, endTs))
        // Grouping so that later code knows when a span or trace is finished.
        .groupBy(schema.dependencyLinkerGroupByFields).fetchLazy();

    Iterator<Iterator<Span>> traces =
        new DependencyLinkV2SpanIterator.ByTraceId(cursor.iterator(), schema.hasTraceIdHigh);

    if (!traces.hasNext()) return Collections.emptyList();

    DependencyLinker linker = new DependencyLinker();

    while (traces.hasNext()) {
      linker.putTrace(traces.next());
    }

    return linker.link();
  }
}
