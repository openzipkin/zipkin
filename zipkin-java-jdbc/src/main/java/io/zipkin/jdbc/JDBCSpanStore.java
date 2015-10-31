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

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.BinaryAnnotation.Type;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import io.zipkin.internal.Pair;
import io.zipkin.internal.Util;
import io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOffsetStep;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.jdbc.JDBCUtils;

import static io.zipkin.BinaryAnnotation.Type.STRING;
import static io.zipkin.internal.Util.checkNotNull;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static io.zipkin.jdbc.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.logging.Level.FINEST;
import static java.util.stream.Collectors.groupingBy;

public final class JDBCSpanStore implements SpanStore {
  private static final Logger LOGGER = Logger.getLogger(JDBCSpanStore.class.getName());
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  {
    System.setProperty("org.jooq.no-logo", "true");
  }

  private final DataSource datasource;
  private final Settings settings;
  private final ExecuteListenerProvider listenerProvider;

  public JDBCSpanStore(DataSource datasource, Settings settings, @Nullable ExecuteListenerProvider listenerProvider) {
    this.datasource = checkNotNull(datasource, "datasource");
    this.settings = checkNotNull(settings, "settings");
    this.listenerProvider = listenerProvider;
  }

  void clear() throws SQLException {
    try (Connection conn = this.datasource.getConnection()) {
      context(conn).truncate(ZIPKIN_SPANS).execute();
      context(conn).truncate(ZIPKIN_ANNOTATIONS).execute();
    }
  }

  @Override
  public void accept(List<Span> spans) {
    if (spans.isEmpty()) return;
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
    final Map<Pair<?>, List<Record>> dbAnnotations;
    try (Connection conn = this.datasource.getConnection()) {
      if (request != null) {
        traceIds = toTraceIdQuery(context(conn), request).fetch(ZIPKIN_SPANS.TRACE_ID);
      }
      spansWithoutAnnotations = context(conn)
          .selectFrom(ZIPKIN_SPANS).where(ZIPKIN_SPANS.TRACE_ID.in(traceIds))
          .orderBy(ZIPKIN_SPANS.START_TS.asc())
          .stream()
          .map(r -> new Span.Builder()
              .traceId(r.getValue(ZIPKIN_SPANS.TRACE_ID))
              .name(r.getValue(ZIPKIN_SPANS.NAME))
              .id(r.getValue(ZIPKIN_SPANS.ID))
              .parentId(r.getValue(ZIPKIN_SPANS.PARENT_ID))
              .debug(r.getValue(ZIPKIN_SPANS.DEBUG))
              .build())
          .collect(groupingBy((Span s) -> s.traceId, LinkedHashMap::new, Collectors.<Span>toList()));

      dbAnnotations = context(conn)
          .selectFrom(ZIPKIN_ANNOTATIONS)
          .where(ZIPKIN_ANNOTATIONS.TRACE_ID.in(spansWithoutAnnotations.keySet()))
          .orderBy(ZIPKIN_ANNOTATIONS.A_TIMESTAMP.asc(), ZIPKIN_ANNOTATIONS.A_KEY.asc())
          .stream()
          .collect(groupingBy((Record a) -> Pair.create(
              a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
              a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)
          ), LinkedHashMap::new, Collectors.<Record>toList())); // LinkedHashMap preserves order while grouping
    } catch (SQLException e) {
      throw new RuntimeException("Error querying for " + request + ": " + e.getMessage());
    }

    List<List<Span>> result = new ArrayList<>(spansWithoutAnnotations.keySet().size());
    for (List<Span> spans : spansWithoutAnnotations.values()) {
      List<Span> trace = new ArrayList<>(spans.size());
      for (Span s : spans) {
        Span.Builder span = new Span.Builder(s);
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
    return DSL.using(new DefaultConfiguration()
        .set(conn)
        .set(JDBCUtils.dialect(conn))
        .set(this.settings)
        .set(this.listenerProvider));
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
    serviceName = serviceName.toLowerCase(); // service names are always lowercase!
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
  public List<DependencyLink> getDependencies(@Nullable Long startTs, @Nullable Long endTs) {
    if (endTs == null) {
      endTs = System.currentTimeMillis() * 1000;
    }
    if (startTs == null) {
      startTs = endTs - MICROSECONDS.convert(1, DAYS);
    }
    try (Connection conn = this.datasource.getConnection()) {
      Map<Long, List<Record3<Long, Long, Long>>> parentChild = context(conn)
          .select(ZIPKIN_SPANS.TRACE_ID, ZIPKIN_SPANS.PARENT_ID, ZIPKIN_SPANS.ID)
          .from(ZIPKIN_SPANS)
          .where(ZIPKIN_SPANS.START_TS.between(startTs, endTs))
          .and(ZIPKIN_SPANS.PARENT_ID.isNotNull())
          .stream().collect(Collectors.groupingBy(r -> r.value1()));

      Map<Pair<Long>, String> traceSpanServiceName = traceSpanServiceName(conn, parentChild.keySet());

      // links are merged by mapping to parent/child and summing corresponding links
      Map<Pair<String>, Long> linkMap = new LinkedHashMap<>();

      parentChild.values().stream().flatMap(List::stream).forEach(r -> {
        String parent = lookup(traceSpanServiceName, Pair.create(r.value1(), r.value2()));
        // can be null if a root span is missing, or the root's span id doesn't eq the trace id
        if (parent != null) {
          String child = lookup(traceSpanServiceName, Pair.create(r.value1(), r.value3()));
          if (child != null) {
            Pair key = Pair.create(parent, child);
            if (linkMap.containsKey(key)) {
              linkMap.put(key, linkMap.get(key) + 1);
            } else {
              linkMap.put(key, 1L);
            }
          }
        }
      });
      List<DependencyLink> result = new ArrayList<>(linkMap.size());
      for (Map.Entry<Pair<String>, Long> entry : linkMap.entrySet()) {
        result.add(DependencyLink.create(entry.getKey()._1, entry.getKey()._2, entry.getValue()));
      }
      return result;
    } catch (SQLException e) {
      throw new RuntimeException("Error querying dependencies between " + startTs + " and " + endTs + ": " + e.getMessage());
    }
  }

  private Map<Pair<Long>, String> traceSpanServiceName(Connection conn, Set<Long> traceIds) {
    return context(conn)
        .selectDistinct(ZIPKIN_ANNOTATIONS.TRACE_ID, ZIPKIN_ANNOTATIONS.SPAN_ID, ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
        .from(ZIPKIN_ANNOTATIONS)
        .where(ZIPKIN_ANNOTATIONS.TRACE_ID.in(traceIds))
        .and(ZIPKIN_ANNOTATIONS.A_KEY.in("sr", "sa"))
        .and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.isNotNull())
        .groupBy(ZIPKIN_ANNOTATIONS.TRACE_ID, ZIPKIN_ANNOTATIONS.SPAN_ID)
        .fetchMap(r -> Pair.create(r.value1(), r.value2()), r -> r.value3());
  }

  @Override
  public void close() {
  }

  static Endpoint endpoint(Record a) {
    String serviceName = a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    if (serviceName == null) {
      return null;
    }
    Short port = a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT);
    return port != null ?
        Endpoint.create(serviceName, a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4), port.intValue())
        : Endpoint.create(serviceName, a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4));
  }

  static SelectOffsetStep<Record1<Long>> toTraceIdQuery(DSLContext context, QueryRequest request) {
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

    SelectConditionStep<Record1<Long>> dsl = context.selectDistinct(ZIPKIN_SPANS.TRACE_ID)
        .from(table)
        .where(lastTimestamp.le(endTs));

    if (request.spanName != null) {
      dsl.and(ZIPKIN_SPANS.NAME.eq(request.spanName));
    }

    for (Map.Entry<String, String> entry : request.binaryAnnotations.entrySet()) {
      dsl.and(keyToTables.get(entry.getKey()).A_VALUE.eq(entry.getValue().getBytes(UTF_8)));
    }
    return dsl.limit(request.limit);
  }

  private static Table<?> join(Table<?> table, ZipkinAnnotations joinTable, String key, int type) {
    return table.join(joinTable)
        .on(ZIPKIN_SPANS.TRACE_ID.eq(joinTable.TRACE_ID))
        .and(ZIPKIN_SPANS.ID.eq(joinTable.SPAN_ID))
        .and(joinTable.A_TYPE.eq(type))
        .and(joinTable.A_KEY.eq(key));
  }

  private static String lookup(Map<Pair<Long>, String> table, Pair<Long> key) {
    String value = table.get(key);
    if (value == null && LOGGER.isLoggable(FINEST)) {
      if (key._1.equals(key._2)) {
        LOGGER.log(FINEST, "could not find service name of root span " + key._1);
      } else {
        LOGGER.log(FINEST, "could not find service name of span " + key);
      }
    }
    return value;
  }
}
