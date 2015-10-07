/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.jdbc;

import static io.zipkin.BinaryAnnotation.Type.STRING;
import static io.zipkin.internal.Util.envOr;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.BinaryAnnotation.Type;
import io.zipkin.Endpoint;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import io.zipkin.internal.Util;
import io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations;

public final class JDBCSpanStore implements SpanStore {

  @Nullable
  public static String mysqlUrlFromEnv() {
    if (System.getenv("MYSQL_USER") == null) return null;
    String mysqlHost = envOr("MYSQL_HOST", "localhost");
    int mysqlPort = envOr("MYSQL_TCP_PORT", 3306);
    String mysqlUser = envOr("MYSQL_USER", "");
    String mysqlPass = envOr("MYSQL_PASS", "");

    return String.format("jdbc:mysql://%s:%s/zipkin?user=%s&password=%s&autoReconnect=true",
        mysqlHost, mysqlPort, mysqlUser, mysqlPass);
  }

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  {
    System.setProperty("org.jooq.no-logo", "true");
  }

  private final DataSource datasource;
  private final Settings settings;

  public JDBCSpanStore(DataSource datasource, Settings settings) {
    this.datasource = datasource;
    this.settings = settings;
  }

  void clear() throws SQLException {
    try (Connection conn = this.datasource.getConnection()) {
      context(conn).truncate(ZIPKIN_SPANS).execute();
      context(conn).truncate(ZIPKIN_ANNOTATIONS).execute();
    }
  }

  @Override
  public void accept(List<Span> spans) {
    try (Connection conn = this.datasource.getConnection()) {
      DSLContext create = context(conn);

      List<Query> inserts = new ArrayList<>();

      for (Span span : spans) {
        Long startTs = span.annotations.stream()
            .map(a -> a.timestamp)
            .min(Comparator.naturalOrder()).orElse(null);

        inserts.add(create.insertInto(ZIPKIN_SPANS)
                .set(ZIPKIN_SPANS.TRACE_ID, span.traceId)
                .set(ZIPKIN_SPANS.ID, span.id)
                .set(ZIPKIN_SPANS.NAME, span.name)
                .set(ZIPKIN_SPANS.PARENT_ID, span.parentId)
                .set(ZIPKIN_SPANS.DEBUG, span.debug)
                .set(ZIPKIN_SPANS.START_TS, startTs)
                .onDuplicateKeyIgnore()
        );

        for (Annotation annotation : span.annotations) {
          InsertSetMoreStep<Record> insert = create.insertInto(ZIPKIN_ANNOTATIONS)
              .set(ZIPKIN_ANNOTATIONS.TRACE_ID, span.traceId)
              .set(ZIPKIN_ANNOTATIONS.SPAN_ID, span.id)
              .set(ZIPKIN_ANNOTATIONS.A_KEY, annotation.value)
              .set(ZIPKIN_ANNOTATIONS.A_TYPE, -1)
              .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, annotation.timestamp);
          if (annotation.endpoint != null) {
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME, annotation.endpoint.serviceName);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, annotation.endpoint.ipv4);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT, annotation.endpoint.port);
          }
          inserts.add(insert.onDuplicateKeyIgnore());
        }

        for (BinaryAnnotation annotation : span.binaryAnnotations) {
          InsertSetMoreStep<Record> insert = create.insertInto(ZIPKIN_ANNOTATIONS)
              .set(ZIPKIN_ANNOTATIONS.TRACE_ID, span.traceId)
              .set(ZIPKIN_ANNOTATIONS.SPAN_ID, span.id)
              .set(ZIPKIN_ANNOTATIONS.A_KEY, annotation.key)
              .set(ZIPKIN_ANNOTATIONS.A_VALUE, annotation.value)
              .set(ZIPKIN_ANNOTATIONS.A_TYPE, annotation.type.value)
              .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, span.endTs());
          if (annotation.endpoint != null) {
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME, annotation.endpoint.serviceName);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, annotation.endpoint.ipv4);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT, annotation.endpoint.port);
          }
          inserts.add(insert.onDuplicateKeyIgnore());
        }
      }
      create.batch(inserts).execute();
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO
    }
  }

  private List<List<Span>> getTraces(@Nullable QueryRequest request, @Nullable List<Long> traceIds) {
    final Map<Long, List<Span>> spansWithoutAnnotations;
    final Map<SpanKey, List<Record>> dbAnnotations;
    try (Connection conn = this.datasource.getConnection()) {
      final SelectConditionStep<?> dsl;
      if (request == null) {
        dsl = context(conn).selectFrom(ZIPKIN_SPANS).where(ZIPKIN_SPANS.TRACE_ID.in(traceIds));
      } else {
        dsl = toSelectCondition(context(conn), request);
      }
      spansWithoutAnnotations = dsl
          .orderBy(ZIPKIN_SPANS.START_TS.asc())
          .limit(request == null ? traceIds.size() : request.limit)
          .fetchGroups(ZIPKIN_SPANS.TRACE_ID, r -> new Span.Builder()
              .traceId(r.getValue(ZIPKIN_SPANS.TRACE_ID))
              .name(r.getValue(ZIPKIN_SPANS.NAME))
              .id(r.getValue(ZIPKIN_SPANS.ID))
              .parentId(r.getValue(ZIPKIN_SPANS.PARENT_ID))
              .debug(r.getValue(ZIPKIN_SPANS.DEBUG))
              .build());

      dbAnnotations = context(conn)
          .selectFrom(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.TRACE_ID.in(spansWithoutAnnotations.keySet()))
          .orderBy(ZIPKIN_ANNOTATIONS.A_TIMESTAMP.asc(), ZIPKIN_ANNOTATIONS.A_KEY.asc())
          .fetch()
          .stream()
          .collect(groupingBy(a -> new SpanKey(
              a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
              a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)
          )));
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + request + ": " + e.getMessage());
    }

    List<List<Span>> result = new ArrayList<>(spansWithoutAnnotations.keySet().size());
    for (List<Span> spans : spansWithoutAnnotations.values()) {
      List<Span> trace = new ArrayList<>(spans.size());
      for (Span s : spans) {
        Span.Builder span = new Span.Builder(s);
        SpanKey key = new SpanKey(s.traceId, s.id);

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
        trace.add(span.build());
      }
      result.add(Util.merge(trace));
    }
    return result;
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request) {
    return getTraces(request, null);
  }

  private DSLContext context(Connection conn) {
    return DSL.using(conn, this.settings);
  }

  @Override
  public List<List<Span>> getTracesByIds(List<Long> traceIds) {
    return traceIds.isEmpty() ? emptyList() : getTraces(null, traceIds);
  }

  @Override
  public List<String> getServiceNames() {
    try (Connection conn = this.datasource.getConnection()) {
      return context(conn)
          .selectDistinct(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
          .from(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.isNotNull())
          .fetch(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + e + ": " + e.getMessage());
    }
  }

  @Override
  public List<String> getSpanNames(String serviceName) {
    if (serviceName == null) return emptyList();
    try (Connection conn = this.datasource.getConnection()) {
      return context(conn)
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
  public void close() {
  }

  static Endpoint endpoint(Record a) {
    String serviceName = a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    if (serviceName == null) {
      return null;
    }
    return Endpoint.create(
        serviceName,
        a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4),
        a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT).intValue());
  }

  static SelectConditionStep<?> toSelectCondition(DSLContext context, QueryRequest request) {
    long endTs = (request.endTs > 0 && request.endTs != Long.MAX_VALUE) ? request.endTs
        : System.currentTimeMillis() / 1000;

    Field<Long> lastTimestamp = ZIPKIN_ANNOTATIONS.A_TIMESTAMP.max().as("last_timestamp");
    Table<Record2<Long, Long>> a1 = context.selectDistinct(ZIPKIN_ANNOTATIONS.TRACE_ID, lastTimestamp)
        .from(ZIPKIN_ANNOTATIONS)
        .where(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(request.serviceName))
        .groupBy(ZIPKIN_ANNOTATIONS.TRACE_ID).asTable();

    Table<?> table = ZIPKIN_SPANS.join(a1)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(a1.field(ZIPKIN_ANNOTATIONS.TRACE_ID)));

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

    SelectConditionStep<?> dsl = context.select(ZIPKIN_SPANS.fields()).from(table)
        .where(lastTimestamp.le(endTs));

    if (request.spanName != null) {
      dsl.and(ZIPKIN_SPANS.NAME.eq(request.spanName));
    }

    for (Map.Entry<String, String> entry : request.binaryAnnotations.entrySet()) {
      dsl.and(keyToTables.get(entry.getKey()).A_VALUE.eq(entry.getValue().getBytes(UTF_8)));
    }
    return dsl;
  }

  private static Table<?> join(Table<?> table, ZipkinAnnotations joinTable, String key, int type) {
    table = table.join(joinTable)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(joinTable.TRACE_ID))
        .and(ZIPKIN_SPANS.ID.eq(joinTable.SPAN_ID))
        .and(joinTable.A_TYPE.eq(type))
        .and(joinTable.A_KEY.eq(key));
    return table;
  }

  private static class SpanKey {

    private final long traceId;
    private final long spanId;

    private SpanKey(long traceId, long spanId) {
      this.traceId = traceId;
      this.spanId = spanId;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof SpanKey) {
        SpanKey that = (SpanKey) o;
        return (this.traceId == that.traceId) && (this.spanId == that.spanId);
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= (this.traceId >>> 32) ^ this.traceId;
      h *= 1000003;
      h ^= (this.spanId >>> 32) ^ this.spanId;
      return h;
    }
  }
}
