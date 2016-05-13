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
package zipkin.storage.jdbc;

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
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.StorageAdapters;

import static zipkin.storage.jdbc.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.jdbc.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class JDBCSpanConsumer implements StorageAdapters.SpanConsumer {
  private final DataSource datasource;
  private final DSLContexts context;

  JDBCSpanConsumer(DataSource datasource, DSLContexts context) {
    this.datasource = datasource;
    this.context = context;
  }

  /** Blocking version of {@link AsyncSpanConsumer#accept} */
  @Override public void accept(List<Span> spans) {
    if (spans.isEmpty()) return;
    try (Connection conn = datasource.getConnection()) {
      DSLContext create = context.get(conn);

      List<Query> inserts = new ArrayList<>();

      for (Span span : spans) {
        Long authoritativeTimestamp = span.timestamp;
        span = ApplyTimestampAndDuration.apply(span);
        Long binaryAnnotationTimestamp = span.timestamp;
        if (binaryAnnotationTimestamp == null) { // fallback if we have no timestamp, yet
          binaryAnnotationTimestamp = System.currentTimeMillis() * 1000;
        }

        Map<TableField<Record, ?>, Object> updateFields = new LinkedHashMap<>();
        if (!span.name.equals("") && !span.name.equals("unknown")) {
          updateFields.put(ZIPKIN_SPANS.NAME, span.name);
        }
        if (authoritativeTimestamp != null) {
          updateFields.put(ZIPKIN_SPANS.START_TS, authoritativeTimestamp);
        }
        if (span.duration != null) {
          updateFields.put(ZIPKIN_SPANS.DURATION, span.duration);
        }

        InsertSetMoreStep<Record> insertSpan = create.insertInto(ZIPKIN_SPANS)
            .set(ZIPKIN_SPANS.TRACE_ID, span.traceId)
            .set(ZIPKIN_SPANS.ID, span.id)
            .set(ZIPKIN_SPANS.PARENT_ID, span.parentId)
            .set(ZIPKIN_SPANS.NAME, span.name)
            .set(ZIPKIN_SPANS.DEBUG, span.debug)
            .set(ZIPKIN_SPANS.START_TS, span.timestamp)
            .set(ZIPKIN_SPANS.DURATION, span.duration);

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
              .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, binaryAnnotationTimestamp);
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
}
