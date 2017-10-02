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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.TableField;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.StorageAdapters;

import static zipkin.internal.ApplyTimestampAndDuration.authoritativeTimestamp;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class MySQLSpanConsumer implements StorageAdapters.SpanConsumer {
  private final DataSource datasource;
  private final DSLContexts context;
  private final Schema schema;

  MySQLSpanConsumer(DataSource datasource, DSLContexts context, Schema schema) {
    this.datasource = datasource;
    this.context = context;
    this.schema = schema;
  }

  /** Blocking version of {@link AsyncSpanConsumer#accept} */
  @Override public void accept(List<Span> spans) {
    if (spans.isEmpty()) return;
    try (Connection conn = datasource.getConnection()) {
      DSLContext create = context.get(conn);

      List<Query> inserts = new ArrayList<>();

      for (Span span : spans) {
        Long overridingTimestamp = authoritativeTimestamp(span);
        Long timestamp = overridingTimestamp != null ? overridingTimestamp : guessTimestamp(span);

        Map<TableField<Record, ?>, Object> updateFields = new LinkedHashMap<>();
        if (!span.name.equals("") && !span.name.equals("unknown")) {
          updateFields.put(ZIPKIN_SPANS.NAME, span.name);
        }
        // replace any tentative timestamp with the authoritative one.
        if (overridingTimestamp != null) {
          updateFields.put(ZIPKIN_SPANS.START_TS, overridingTimestamp);
        }
        if (span.duration != null) {
          updateFields.put(ZIPKIN_SPANS.DURATION, span.duration);
        }
        if (span.parentId != null) {
          updateFields.put(ZIPKIN_SPANS.PARENT_ID, span.parentId);
        }

        InsertSetMoreStep<Record> insertSpan = create.insertInto(ZIPKIN_SPANS)
            .set(ZIPKIN_SPANS.TRACE_ID, span.traceId)
            .set(ZIPKIN_SPANS.ID, span.id)
            .set(ZIPKIN_SPANS.PARENT_ID, span.parentId)
            .set(ZIPKIN_SPANS.NAME, span.name)
            .set(ZIPKIN_SPANS.DEBUG, span.debug)
            .set(ZIPKIN_SPANS.START_TS, timestamp)
            .set(ZIPKIN_SPANS.DURATION, span.duration);

        if (span.traceIdHigh != 0 && schema.hasTraceIdHigh) {
          insertSpan.set(ZIPKIN_SPANS.TRACE_ID_HIGH, span.traceIdHigh);
        }

        inserts.add(updateFields.isEmpty() ?
            insertSpan.onDuplicateKeyIgnore() :
            insertSpan.onDuplicateKeyUpdate().set(updateFields));

        for (Annotation annotation : span.annotations) {
          InsertSetMoreStep<Record> insert = create.insertInto(ZIPKIN_ANNOTATIONS)
              .set(ZIPKIN_ANNOTATIONS.TRACE_ID, span.traceId)
              .set(ZIPKIN_ANNOTATIONS.SPAN_ID, span.id)
              .set(ZIPKIN_ANNOTATIONS.A_KEY, annotation.value)
              .set(ZIPKIN_ANNOTATIONS.A_TYPE, -1)
              .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, annotation.timestamp);
          if (span.traceIdHigh != 0 && schema.hasTraceIdHigh) {
            insert.set(ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, span.traceIdHigh);
          }
          if (annotation.endpoint != null) {
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME, annotation.endpoint.serviceName);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, annotation.endpoint.ipv4);
            if (annotation.endpoint.ipv6 != null && schema.hasIpv6) {
              insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6, annotation.endpoint.ipv6);
            }
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
              .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, timestamp);
          if (span.traceIdHigh != 0 && schema.hasTraceIdHigh) {
            insert.set(ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, span.traceIdHigh);
          }
          if (annotation.endpoint != null) {
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME, annotation.endpoint.serviceName);
            insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, annotation.endpoint.ipv4);
            if (annotation.endpoint.ipv6 != null && schema.hasIpv6) {
              insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6, annotation.endpoint.ipv6);
            }
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
}
