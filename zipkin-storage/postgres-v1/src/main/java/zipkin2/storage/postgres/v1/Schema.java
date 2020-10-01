/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.postgres.v1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Row2;
import org.jooq.SelectOffsetStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinAnnotations;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinDependencies;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinSpans;


import static org.jooq.impl.DSL.row;

final class Schema {
  final List<Field<?>> spanIdFields;
  final List<Field<?>> spanFields;
  final List<Field<?>> annotationFields;
  final List<Field<?>> dependencyLinkerFields;
  final List<Field<?>> dependencyLinkerGroupByFields;
  final List<Field<?>> dependencyLinkFields;
  final boolean hasTraceIdHigh;
  final boolean hasPreAggregatedDependencies;
  final boolean hasIpv6;
  final boolean hasErrorCount;
  final boolean hasRemoteServiceName;
  final boolean strictTraceId;

  Schema(DataSource datasource, DSLContexts context, boolean strictTraceId) {
    hasTraceIdHigh = HasTraceIdHigh.test(datasource, context);
    hasPreAggregatedDependencies = HasPreAggregatedDependencies.test(datasource, context);
    hasIpv6 = HasIpv6.test(datasource, context);
    hasErrorCount = HasErrorCount.test(datasource, context);
    hasRemoteServiceName = HasRemoteServiceName.test(datasource, context);
    this.strictTraceId = strictTraceId;

    spanIdFields = list(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH, ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
    spanFields = list(ZipkinSpans.ZIPKIN_SPANS.fields());
    spanIdFields.remove(ZipkinSpans.ZIPKIN_SPANS.REMOTE_SERVICE_NAME); // not used to recreate the span
    annotationFields = list(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.fields());
    dependencyLinkFields = list(ZipkinDependencies.ZIPKIN_DEPENDENCIES.fields());
    dependencyLinkerFields =
        list(
            ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH,
            ZipkinSpans.ZIPKIN_SPANS.TRACE_ID,
            ZipkinSpans.ZIPKIN_SPANS.PARENT_ID,
            ZipkinSpans.ZIPKIN_SPANS.ID,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_KEY,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_TYPE,
            ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
    dependencyLinkerGroupByFields = new ArrayList<>(dependencyLinkerFields);
    dependencyLinkerGroupByFields.remove(ZipkinSpans.ZIPKIN_SPANS.PARENT_ID);
    if (!hasTraceIdHigh) {
      spanIdFields.remove(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH);
      spanFields.remove(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH);
      annotationFields.remove(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH);
      dependencyLinkerFields.remove(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH);
      dependencyLinkerGroupByFields.remove(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH);
    }
    if (!hasIpv6) {
      annotationFields.remove(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6);
    }
    if (!hasErrorCount) {
      dependencyLinkFields.remove(ZipkinDependencies.ZIPKIN_DEPENDENCIES.ERROR_COUNT);
    }
  }

  Condition joinCondition(ZipkinAnnotations annotationTable) {
    if (hasTraceIdHigh) {
      return ZipkinSpans.ZIPKIN_SPANS
          .TRACE_ID_HIGH
          .eq(annotationTable.TRACE_ID_HIGH)
          .and(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID.eq(annotationTable.TRACE_ID))
          .and(ZipkinSpans.ZIPKIN_SPANS.ID.eq(annotationTable.SPAN_ID));
    } else {
      return ZipkinSpans.ZIPKIN_SPANS
          .TRACE_ID
          .eq(annotationTable.TRACE_ID)
          .and(ZipkinSpans.ZIPKIN_SPANS.ID.eq(annotationTable.SPAN_ID));
    }
  }

  /** Returns a mutable list */
  static <T> List<T> list(T... elements) {
    return new ArrayList<>(Arrays.asList(elements));
  }

  Condition spanTraceIdCondition(SelectOffsetStep<? extends Record> traceIdQuery) {
    if (hasTraceIdHigh && strictTraceId) {
      Result<? extends Record> result = traceIdQuery.fetch();
      List<Row2<Long, Long>> traceIds = new ArrayList<>(result.size());
      for (Record r : result) {
        traceIds.add(
            DSL.row(r.get(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH), r.get(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID)));
      }
      return row(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH, ZipkinSpans.ZIPKIN_SPANS.TRACE_ID).in(traceIds);
    } else {
      List<Long> traceIds = traceIdQuery.fetch(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID);
      return ZipkinSpans.ZIPKIN_SPANS.TRACE_ID.in(traceIds);
    }
  }

  Condition spanTraceIdCondition(long traceIdHigh, long traceIdLow) {
    return traceIdHigh != 0L && hasTraceIdHigh
        ? row(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH, ZipkinSpans.ZIPKIN_SPANS.TRACE_ID).eq(traceIdHigh, traceIdLow)
        : ZipkinSpans.ZIPKIN_SPANS.TRACE_ID.eq(traceIdLow);
  }

  Condition spanTraceIdCondition(Set<Pair> traceIds) {
    return traceIdCondition(ZipkinSpans.ZIPKIN_SPANS.TRACE_ID_HIGH, ZipkinSpans.ZIPKIN_SPANS.TRACE_ID, traceIds);
  }

  Condition annotationsTraceIdCondition(Set<Pair> traceIds) {
    return traceIdCondition(
        ZipkinAnnotations.ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, ZipkinAnnotations.ZIPKIN_ANNOTATIONS.TRACE_ID, traceIds);
  }

  Condition traceIdCondition(
    TableField<Record, Long> TRACE_ID_HIGH,
    TableField<Record, Long> TRACE_ID, Set<Pair> traceIds
  ) {
    boolean hasTraceIdHigh = false;
    for (Pair traceId : traceIds) {
      if (traceId.left != 0) {
        hasTraceIdHigh = true;
        break;
      }
    }
    if (hasTraceIdHigh && strictTraceId) {
      Row2[] result = new Row2[traceIds.size()];
      int i = 0;
      for (Pair traceId128 : traceIds) {
        result[i++] = row(traceId128.left, traceId128.right);
      }
      return row(TRACE_ID_HIGH, TRACE_ID).in(result);
    } else {
      Long[] result = new Long[traceIds.size()];
      int i = 0;
      for (Pair traceId128 : traceIds) {
        result[i++] = traceId128.right;
      }
      return TRACE_ID.in(result);
    }
  }

  /** returns the default value if the column doesn't exist or the result was null */
  static <T> T maybeGet(Record record, TableField<Record, T> field, T defaultValue) {
    if (record.fieldsRow().indexOf(field) < 0) {
      return defaultValue;
    } else {
      T result = record.get(field);
      return result != null ? result : defaultValue;
    }
  }
}
