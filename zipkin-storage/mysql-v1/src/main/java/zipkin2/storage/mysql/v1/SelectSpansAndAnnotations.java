/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.mysql.v1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Row3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectField;
import org.jooq.SelectOffsetStep;
import org.jooq.TableOnConditionStep;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static java.util.stream.Collectors.groupingBy;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.row;
import static zipkin2.storage.mysql.v1.MySQLSpanConsumer.UTF_8;
import static zipkin2.storage.mysql.v1.Schema.maybeGet;
import static zipkin2.storage.mysql.v1.SelectAnnotationServiceNames.localServiceNameCondition;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

abstract class SelectSpansAndAnnotations implements Function<DSLContext, List<Span>> {
  static final class Factory {
    final Schema schema;
    final boolean strictTraceId;

    Factory(Schema schema, boolean strictTraceId) {
      this.schema = schema;
      this.strictTraceId = strictTraceId;
    }

    SelectSpansAndAnnotations create(long traceIdHigh, long traceIdLow) {
      if (traceIdHigh != 0L && !strictTraceId) traceIdHigh = 0L;
      long finalTraceIdHigh = traceIdHigh;
      return new SelectSpansAndAnnotations(schema) {
        @Override
        Condition traceIdCondition(DSLContext context) {
          return schema.spanTraceIdCondition(finalTraceIdHigh, traceIdLow);
        }
      };
    }

    SelectSpansAndAnnotations create(QueryRequest request) {
      return new SelectSpansAndAnnotations(schema) {
        @Override
        Condition traceIdCondition(DSLContext context) {
          return schema.spanTraceIdCondition(toTraceIdQuery(context, request));
        }
      };
    }
  }

  final Schema schema;

  SelectSpansAndAnnotations(Schema schema) {
    this.schema = schema;
  }

  abstract Condition traceIdCondition(DSLContext context);

  @Override
  public List<Span> apply(DSLContext context) {
    final Map<Pair, List<V1Span.Builder>> spansWithoutAnnotations;
    final Map<Row3<Long, Long, Long>, List<Record>> dbAnnotations;

    spansWithoutAnnotations =
        context
            .select(schema.spanFields)
            .from(ZIPKIN_SPANS)
            .where(traceIdCondition(context))
            .stream()
            .map(
                r ->
                    V1Span.newBuilder()
                        .traceIdHigh(maybeGet(r, ZIPKIN_SPANS.TRACE_ID_HIGH, 0L))
                        .traceId(r.getValue(ZIPKIN_SPANS.TRACE_ID))
                        .name(r.getValue(ZIPKIN_SPANS.NAME))
                        .id(r.getValue(ZIPKIN_SPANS.ID))
                        .parentId(maybeGet(r, ZIPKIN_SPANS.PARENT_ID, 0L))
                        .timestamp(maybeGet(r, ZIPKIN_SPANS.START_TS, 0L))
                        .duration(maybeGet(r, ZIPKIN_SPANS.DURATION, 0L))
                        .debug(r.getValue(ZIPKIN_SPANS.DEBUG)))
            .collect(
                groupingBy(
                    s -> new Pair(s.traceIdHigh(), s.traceId()),
                    LinkedHashMap::new,
                    Collectors.toList()));

    dbAnnotations =
        context
            .select(schema.annotationFields)
            .from(ZIPKIN_ANNOTATIONS)
            .where(schema.annotationsTraceIdCondition(spansWithoutAnnotations.keySet()))
            .orderBy(ZIPKIN_ANNOTATIONS.A_TIMESTAMP.asc(), ZIPKIN_ANNOTATIONS.A_KEY.asc())
            .stream()
            .collect(
                groupingBy(
                    (Record a) ->
                        row(
                            maybeGet(a, ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, 0L),
                            a.getValue(ZIPKIN_ANNOTATIONS.TRACE_ID),
                            a.getValue(ZIPKIN_ANNOTATIONS.SPAN_ID)),
                    LinkedHashMap::new,
                    Collectors.toList())); // LinkedHashMap preserves order while grouping

    V1SpanConverter converter = V1SpanConverter.create();
    List<Span> allSpans = new ArrayList<>(spansWithoutAnnotations.size());
    for (List<V1Span.Builder> spans : spansWithoutAnnotations.values()) {
      for (V1Span.Builder span : spans) {
        Row3<Long, Long, Long> key = row(span.traceIdHigh(), span.traceId(), span.id());
        if (dbAnnotations.containsKey(key)) {
          for (Record a : dbAnnotations.get(key)) {
            Endpoint endpoint = endpoint(a);
            processAnnotationRecord(a, span, endpoint);
          }
        }
        converter.convert(span.build(), allSpans);
      }
    }
    return allSpans;
  }

  static void processAnnotationRecord(Record a, V1Span.Builder span, @Nullable Endpoint endpoint) {
    Integer type = a.getValue(ZIPKIN_ANNOTATIONS.A_TYPE);
    if (type == null) return;
    if (type == -1) {
      span.addAnnotation(
          a.getValue(ZIPKIN_ANNOTATIONS.A_TIMESTAMP),
          a.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
          endpoint);
    } else {
      switch (type) {
        case V1BinaryAnnotation.TYPE_STRING:
          span.addBinaryAnnotation(
              a.getValue(ZIPKIN_ANNOTATIONS.A_KEY),
              new String(a.getValue(ZIPKIN_ANNOTATIONS.A_VALUE), UTF_8),
              endpoint);
          break;
        case V1BinaryAnnotation.TYPE_BOOLEAN:
          // address annotations require an endpoint
          if (endpoint == null) break;
          String aKey = a.getValue(ZIPKIN_ANNOTATIONS.A_KEY);
          // ensure we are only processing address annotations
          if (!aKey.equals("sa") && !aKey.equals("ca") && !aKey.equals("ma")) break;
          byte[] value = a.getValue(ZIPKIN_ANNOTATIONS.A_VALUE);
          // address annotations are a single byte of 1
          if (value == null || value.length != 1 || value[0] != 1) break;
          span.addBinaryAnnotation(a.getValue(ZIPKIN_ANNOTATIONS.A_KEY), endpoint);
          break;
        default:
          // other values unsupported
      }
    }
  }

  SelectOffsetStep<? extends Record> toTraceIdQuery(DSLContext context, QueryRequest request) {
    long endTs = request.endTs() * 1000;

    TableOnConditionStep<?> table =
        ZIPKIN_SPANS.join(ZIPKIN_ANNOTATIONS).on(schema.joinCondition(ZIPKIN_ANNOTATIONS));

    int i = 0;
    for (Map.Entry<String, String> kv : request.annotationQuery().entrySet()) {
      ZipkinAnnotations aTable = ZIPKIN_ANNOTATIONS.as("a" + i++);
      if (kv.getValue().isEmpty()) {
        table =
            maybeOnService(
                table
                    .join(aTable)
                    .on(schema.joinCondition(aTable))
                    .and(aTable.A_KEY.eq(kv.getKey())),
                aTable,
                request.serviceName());
      } else {
        table =
            maybeOnService(
                table
                    .join(aTable)
                    .on(schema.joinCondition(aTable))
                    .and(aTable.A_TYPE.eq(V1BinaryAnnotation.TYPE_STRING))
                    .and(aTable.A_KEY.eq(kv.getKey()))
                    .and(aTable.A_VALUE.eq(kv.getValue().getBytes(UTF_8))),
                aTable,
                request.serviceName());
      }
    }

    List<SelectField<?>> distinctFields = new ArrayList<>(schema.spanIdFields);
    distinctFields.add(max(ZIPKIN_SPANS.START_TS));
    SelectConditionStep<Record> dsl = context.selectDistinct(distinctFields)
      .from(table)
      .where(ZIPKIN_SPANS.START_TS.between(endTs - request.lookback() * 1000, endTs));

    if (request.serviceName() != null) {
      dsl.and(localServiceNameCondition()
        .and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(request.serviceName())));
    }

    if (request.remoteServiceName() != null) {
      dsl.and(ZIPKIN_SPANS.REMOTE_SERVICE_NAME.eq(request.remoteServiceName()));
    }

    if (request.spanName() != null) {
      dsl.and(ZIPKIN_SPANS.NAME.eq(request.spanName()));
    }

    if (request.minDuration() != null && request.maxDuration() != null) {
      dsl.and(ZIPKIN_SPANS.DURATION.between(request.minDuration(), request.maxDuration()));
    } else if (request.minDuration() != null) {
      dsl.and(ZIPKIN_SPANS.DURATION.greaterOrEqual(request.minDuration()));
    }
    return dsl.groupBy(schema.spanIdFields)
        .orderBy(max(ZIPKIN_SPANS.START_TS).desc())
        .limit(request.limit());
  }

  static TableOnConditionStep<?> maybeOnService(
      TableOnConditionStep<Record> table, ZipkinAnnotations aTable, String serviceName) {
    if (serviceName == null) return table;
    return table.and(aTable.ENDPOINT_SERVICE_NAME.eq(serviceName));
  }

  static Endpoint endpoint(Record a) {
    Endpoint.Builder result =
        Endpoint.newBuilder()
            .serviceName(a.getValue(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME))
            .port(Schema.maybeGet(a, ZIPKIN_ANNOTATIONS.ENDPOINT_PORT, (short) 0));
    int ipv4 = maybeGet(a, ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, 0);
    if (ipv4 != 0) {
      result.parseIp( // allocation is ok here as Endpoint.ipv4Bytes would anyway
          new byte[] {
            (byte) (ipv4 >> 24 & 0xff),
            (byte) (ipv4 >> 16 & 0xff),
            (byte) (ipv4 >> 8 & 0xff),
            (byte) (ipv4 & 0xff)
          });
    }
    result.parseIp(Schema.maybeGet(a, ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6, null));
    return result.build();
  }
}
