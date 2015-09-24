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

import com.google.auto.value.AutoValue;
import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.Constants;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import io.zipkin.Trace;
import io.zipkin.internal.Nullable;
import io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations;
import io.zipkin.spanstore.QueryException;
import io.zipkin.spanstore.QueryRequest;
import io.zipkin.spanstore.SpanStore;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import static io.zipkin.BinaryAnnotation.Type.STRING;
import static io.zipkin.internal.Util.envOr;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;

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
    try (Connection conn = datasource.getConnection()) {
      context(conn).truncate(ZIPKIN_SPANS).execute();
      context(conn).truncate(ZIPKIN_ANNOTATIONS).execute();
    }
  }

  @Override
  public void accept(List<Span> spans) {
    try (Connection conn = datasource.getConnection()) {
      DSLContext create = context(conn);

      List<Query> inserts = new ArrayList<>();

      for (Span span : spans) {
        Long createdTs = span.annotations().stream()
            .map(Annotation::timestamp)
            .min(Comparator.naturalOrder()).orElse(null);

        inserts.add(create.insertInto(ZIPKIN_SPANS)
                .set(ZIPKIN_SPANS.TRACE_ID, span.traceId())
                .set(ZIPKIN_SPANS.ID, span.id())
                .set(ZIPKIN_SPANS.NAME, span.name())
                .set(ZIPKIN_SPANS.PARENT_ID, span.parentId())
                .set(ZIPKIN_SPANS.DEBUG, span.debug())
                .set(ZIPKIN_SPANS.FIRST_TIMESTAMP, createdTs)
                .onDuplicateKeyIgnore()
        );

        for (Annotation annotation : span.annotations()) {
          InsertSetMoreStep<Record> insert = create.insertInto(ZIPKIN_ANNOTATIONS)
              .set(ZIPKIN_ANNOTATIONS.TRACE_ID, span.traceId())
              .set(ZIPKIN_ANNOTATIONS.SPAN_ID, span.id())
              .set(ZIPKIN_ANNOTATIONS.KEY, annotation.value())
              .set(ZIPKIN_ANNOTATIONS.TYPE, -1)
              .set(ZIPKIN_ANNOTATIONS.TIMESTAMP, annotation.timestamp());
          if (annotation.host() != null) {
            insert.set(ZIPKIN_ANNOTATIONS.HOST_IPV4, annotation.host().ipv4());
            insert.set(ZIPKIN_ANNOTATIONS.HOST_PORT, annotation.host().port());
            insert.set(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME, annotation.host().serviceName());
          }
          inserts.add(insert.onDuplicateKeyIgnore());
        }

        for (BinaryAnnotation annotation : span.binaryAnnotations()) {
          InsertSetMoreStep<Record> insert = create.insertInto(ZIPKIN_ANNOTATIONS)
              .set(ZIPKIN_ANNOTATIONS.TRACE_ID, span.traceId())
              .set(ZIPKIN_ANNOTATIONS.SPAN_ID, span.id())
              .set(ZIPKIN_ANNOTATIONS.KEY, annotation.key())
              .set(ZIPKIN_ANNOTATIONS.VALUE, annotation.value())
              .set(ZIPKIN_ANNOTATIONS.TYPE, annotation.type().value())
              .set(ZIPKIN_ANNOTATIONS.TIMESTAMP, createdTs);
          if (annotation.host() != null) {
            insert.set(ZIPKIN_ANNOTATIONS.HOST_IPV4, annotation.host().ipv4());
            insert.set(ZIPKIN_ANNOTATIONS.HOST_PORT, annotation.host().port());
            insert.set(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME, annotation.host().serviceName());
          }
          inserts.add(insert.onDuplicateKeyIgnore());
        }
      }
      create.batch(inserts).execute();
    } catch (SQLException e) {
      throw new RuntimeException(e); // TODO
    }
  }

  @Override
  public List<Trace> getTraces(QueryRequest request) throws QueryException {
    return getTraces(request, null, request.adjustClockSkew());
  }

  private List<Trace> getTraces(@Nullable QueryRequest request, @Nullable List<Long> traceIds,
                                boolean adjustClockSkew) {
    final Map<Long, List<Span>> spansWithoutAnnotations;
    final Map<SpanKey, List<Record>> dbAnnotations;
    try (Connection conn = datasource.getConnection()) {

      final SelectConditionStep<?> dsl;
      if (request == null) {
        dsl = context(conn).selectFrom(ZIPKIN_SPANS).where(ZIPKIN_SPANS.TRACE_ID.in(traceIds));
      } else {
        dsl = toSelectCondition(context(conn), request);
      }
      spansWithoutAnnotations = dsl
          .orderBy(ZIPKIN_SPANS.FIRST_TIMESTAMP.asc())
          .fetchGroups(ZIPKIN_SPANS.TRACE_ID, r -> Span.builder()
              .traceId(r.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID))
              .name(r.getValue(ZIPKIN_SPANS.NAME))
              .id(r.getValue(ZIPKIN_SPANS.ID))
              .parentId(r.getValue(ZIPKIN_SPANS.PARENT_ID))
              .debug(r.getValue(ZIPKIN_SPANS.DEBUG))
              .annotations(emptyList())
              .binaryAnnotations(emptyList())
              .build());

      dbAnnotations = context(conn)
          .selectFrom(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.TRACE_ID.in(spansWithoutAnnotations.keySet()))
          .orderBy(ZIPKIN_ANNOTATIONS.TIMESTAMP.asc())
          .fetch()
          .stream()
          .collect(groupingBy(a -> SpanKey.create(
              a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
              a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)
          )));
    } catch (SQLException e) {
      throw new QueryException("Error querying for " + request + ": " + e.getMessage());
    }

    List<Trace> result = new ArrayList<>(spansWithoutAnnotations.keySet().size());
    for (List<Span> spans : spansWithoutAnnotations.values()) {
      List<Span> trace = new ArrayList<>(spans.size());
      for (Span s : spans) {
        Span.Builder span = Span.builder(s);
        SpanKey key = SpanKey.create(s.traceId(), s.id());

        if (dbAnnotations.containsKey(key)) {
          List<Annotation> annotations = new ArrayList<>();
          List<BinaryAnnotation> binaryAnnotations = new ArrayList<>();
          for (Record a : dbAnnotations.get(key)) {
            Endpoint host = host(a);
            int type = a.getValue(ZIPKIN_ANNOTATIONS.TYPE);
            if (type == -1) {
              annotations.add(Annotation.builder()
                      .value(a.getValue(ZIPKIN_ANNOTATIONS.KEY))
                      .timestamp(a.getValue(ZIPKIN_ANNOTATIONS.TIMESTAMP))
                      .host(host)
                      .build()
              );
            } else {
              binaryAnnotations.add(BinaryAnnotation.builder()
                  .key(a.getValue(ZIPKIN_ANNOTATIONS.KEY))
                  .value(a.getValue(ZIPKIN_ANNOTATIONS.VALUE))
                  .type(BinaryAnnotation.Type.fromValue(type))
                  .host(host)
                  .build());
            }
          }
          span.annotations(annotations);
          span.binaryAnnotations(binaryAnnotations);
        }
        trace.add(span.build());
      }
      result.add(Trace.create(trace));
    }
    return result;
  }

  private DSLContext context(Connection conn) {
    return DSL.using(conn, settings);
  }

  @Override
  public List<Trace> getTracesByIds(List<Long> traceIds, boolean adjustClockSkew)
      throws QueryException {
    return traceIds.isEmpty() ? emptyList() : getTraces(null, traceIds, adjustClockSkew);
  }

  @Override
  public Set<String> getServiceNames() throws QueryException {
    try (Connection conn = datasource.getConnection()) {
      return context(conn)
          .selectDistinct(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME)
          .from(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME.isNotNull())
          .fetchSet(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME);
    } catch (SQLException e) {
      throw new QueryException("Error querying for " + e + ": " + e.getMessage());
    }
  }

  @Override
  public Set<String> getSpanNames(String serviceName) throws QueryException {
    if (serviceName == null) return emptySet();
    try (Connection conn = datasource.getConnection()) {
      return context(conn)
          .selectDistinct(ZIPKIN_SPANS.NAME)
          .from(ZIPKIN_SPANS)
          .join(ZIPKIN_ANNOTATIONS)
          .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID))
          .and(ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID))
          .where(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME.eq(serviceName))
          .fetchSet(ZIPKIN_SPANS.NAME);
    } catch (SQLException e) {
      throw new QueryException("Error querying for " + serviceName + ": " + e.getMessage());
    }
  }

  @AutoValue
  static abstract class SpanKey {

    abstract long traceId();

    abstract long spanId();

    static SpanKey create(long traceId, long spanId) {
      return new AutoValue_JDBCSpanStore_SpanKey(traceId, spanId);
    }
  }

  static Endpoint host(Record a) {
    String serviceName = a.getValue(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME);
    return serviceName != null ? Endpoint.builder()
        .ipv4(a.getValue(ZIPKIN_ANNOTATIONS.HOST_IPV4))
        .port(a.getValue(ZIPKIN_ANNOTATIONS.HOST_PORT))
        .serviceName(serviceName).build() : null;
  }

  static SelectConditionStep<?> toSelectCondition(DSLContext context, QueryRequest request) {
    long endTs = (request.endTs() > 0 && request.endTs() != Long.MAX_VALUE) ? request.endTs()
        : System.currentTimeMillis() / 1000;

    Map<String, String> binaryAnnotations =
        request.binaryAnnotations() != null ? request.binaryAnnotations() : Collections.emptyMap();

    Table<?> table = ZIPKIN_SPANS.join(ZIPKIN_ANNOTATIONS)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(ZIPKIN_ANNOTATIONS.TRACE_ID))
        .and(ZIPKIN_SPANS.ID.eq(ZIPKIN_ANNOTATIONS.SPAN_ID));

    for (String key : binaryAnnotations.keySet()) {
      ZipkinAnnotations joinTable = ZIPKIN_ANNOTATIONS.as(key);
      table = table.join(joinTable)
          .on(ZIPKIN_SPANS.TRACE_ID.eq(joinTable.TRACE_ID))
          .and(ZIPKIN_SPANS.ID.eq(joinTable.SPAN_ID))
          .and(joinTable.TYPE.eq(STRING.value()))
          .and(joinTable.KEY.eq(key));
    }

    SelectConditionStep<?> dsl = context.select(ZIPKIN_SPANS.fields()).from(table)
        .where(ZIPKIN_ANNOTATIONS.HOST_SERVICE_NAME.eq(request.serviceName()))
        .and(ZIPKIN_SPANS.FIRST_TIMESTAMP.lessThan(endTs));

    if (request.spanName() != null) {
      dsl.and(ZIPKIN_SPANS.NAME.eq(request.spanName()));
    }

    if (request.annotations() != null && !request.annotations().isEmpty()) {
      List<String> filtered = new ArrayList<>(request.annotations());
      filtered.removeAll(Constants.CORE_ANNOTATIONS); // don't return core annotations
      dsl = dsl.and(ZIPKIN_ANNOTATIONS.KEY.in(filtered)).and(ZIPKIN_ANNOTATIONS.TYPE.eq(-1));
    }

    for (Map.Entry<String, String> entry : binaryAnnotations.entrySet()) {
      dsl = dsl.and(ZIPKIN_ANNOTATIONS.as(entry.getKey()).VALUE
          .eq(entry.getValue().getBytes(UTF_8)));
    }
    return dsl;
  }
}