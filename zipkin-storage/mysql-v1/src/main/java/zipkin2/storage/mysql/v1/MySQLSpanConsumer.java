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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.TableField;
import zipkin2.Call;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.storage.SpanConsumer;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class MySQLSpanConsumer implements SpanConsumer {
  static final Charset UTF_8 = Charset.forName("UTF-8");
  static final byte[] ONE = {1};

  final DataSourceCall.Factory dataSourceCallFactory;
  final Schema schema;

  MySQLSpanConsumer(DataSourceCall.Factory dataSourceCallFactory, Schema schema) {
    this.dataSourceCallFactory = dataSourceCallFactory;
    this.schema = schema;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    return dataSourceCallFactory.create(new BatchInsertSpans(spans, schema));
  }

  static final class BatchInsertSpans implements Function<DSLContext, Void> {
    final List<Span> spans;
    final Schema schema;

    BatchInsertSpans(List<Span> spans, Schema schema) {
      this.spans = spans;
      this.schema = schema;
    }

    @Override
    public Void apply(DSLContext create) {
      List<Query> inserts = new ArrayList<>();
      V2SpanConverter v2SpanConverter = V2SpanConverter.create();

      for (Span v2 : spans) {
        Endpoint ep = v2.localEndpoint();
        long timestamp = v2.timestampAsLong();

        V1Span v1Span = v2SpanConverter.convert(v2);

        long traceId, spanId;
        InsertSetMoreStep<Record> insertSpan =
            create
                .insertInto(ZIPKIN_SPANS)
                .set(ZIPKIN_SPANS.TRACE_ID, traceId = v1Span.traceId())
                .set(ZIPKIN_SPANS.ID, spanId = v1Span.id())
                .set(ZIPKIN_SPANS.DEBUG, v1Span.debug());

        Map<TableField<Record, ?>, Object> updateFields = new LinkedHashMap<>();
        if (timestamp != 0L) {
          // tentatively we can use even a shared timestamp
          insertSpan.set(ZIPKIN_SPANS.START_TS, timestamp);
          // replace any tentative timestamp with the authoritative one.
          if (!Boolean.TRUE.equals(v2.shared())) updateFields.put(ZIPKIN_SPANS.START_TS, timestamp);
        }

        updateName(v1Span.name(), ZIPKIN_SPANS.NAME, insertSpan, updateFields);
        if (schema.hasRemoteServiceName) {
          updateName(v2.remoteServiceName(), ZIPKIN_SPANS.REMOTE_SERVICE_NAME, insertSpan, updateFields);
        }

        long duration = v1Span.duration();
        if (duration != 0L) {
          insertSpan.set(ZIPKIN_SPANS.DURATION, duration);
          updateFields.put(ZIPKIN_SPANS.DURATION, duration);
        }

        if (v1Span.parentId() != 0) {
          insertSpan.set(ZIPKIN_SPANS.PARENT_ID, v1Span.parentId());
          updateFields.put(ZIPKIN_SPANS.PARENT_ID, v1Span.parentId());
        }

        long traceIdHigh = schema.hasTraceIdHigh ? v1Span.traceIdHigh() : 0L;
        if (traceIdHigh != 0L) {
          insertSpan.set(ZIPKIN_SPANS.TRACE_ID_HIGH, traceIdHigh);
        }

        inserts.add(
            updateFields.isEmpty()
                ? insertSpan.onDuplicateKeyIgnore()
                : insertSpan.onDuplicateKeyUpdate().set(updateFields));

        int ipv4 =
            ep != null && ep.ipv4Bytes() != null ? ByteBuffer.wrap(ep.ipv4Bytes()).getInt() : 0;
        for (V1Annotation a : v1Span.annotations()) {
          InsertSetMoreStep<Record> insert =
              create
                  .insertInto(ZIPKIN_ANNOTATIONS)
                  .set(ZIPKIN_ANNOTATIONS.TRACE_ID, traceId)
                  .set(ZIPKIN_ANNOTATIONS.SPAN_ID, spanId)
                  .set(ZIPKIN_ANNOTATIONS.A_KEY, a.value())
                  .set(ZIPKIN_ANNOTATIONS.A_TYPE, -1)
                  .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, a.timestamp());
          if (traceIdHigh != 0L) {
            insert.set(ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, traceIdHigh);
          }
          addEndpoint(insert, ep, ipv4);
          inserts.add(insert.onDuplicateKeyIgnore());
        }

        for (V1BinaryAnnotation ba : v1Span.binaryAnnotations()) {
          InsertSetMoreStep<Record> insert =
              create
                  .insertInto(ZIPKIN_ANNOTATIONS)
                  .set(ZIPKIN_ANNOTATIONS.TRACE_ID, traceId)
                  .set(ZIPKIN_ANNOTATIONS.SPAN_ID, spanId)
                  .set(ZIPKIN_ANNOTATIONS.A_KEY, ba.key())
                  .set(ZIPKIN_ANNOTATIONS.A_TYPE, ba.type())
                  .set(ZIPKIN_ANNOTATIONS.A_TIMESTAMP, timestamp);
          if (traceIdHigh != 0) {
            insert.set(ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, traceIdHigh);
          }
          if (ba.stringValue() != null) {
            insert.set(ZIPKIN_ANNOTATIONS.A_VALUE, ba.stringValue().getBytes(UTF_8));
            addEndpoint(insert, ep, ipv4);
          } else { // add the address annotation
            insert.set(ZIPKIN_ANNOTATIONS.A_VALUE, ONE);
            Endpoint nextEp = ba.endpoint();
            addEndpoint(
                insert,
                nextEp,
                nextEp.ipv4Bytes() != null ? ByteBuffer.wrap(nextEp.ipv4Bytes()).getInt() : 0);
          }
          inserts.add(insert.onDuplicateKeyIgnore());
        }
      }
      create.batch(inserts).execute();
      return null;
    }

    void addEndpoint(InsertSetMoreStep<Record> insert, Endpoint ep, int ipv4) {
      if (ep == null) return;
      // old code wrote empty service names
      String serviceName = ep.serviceName() != null ? ep.serviceName() : "";
      insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME, serviceName);
      if (ipv4 != 0) {
        insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV4, ipv4);
      }
      if (ep.ipv6Bytes() != null && schema.hasIpv6) {
        insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6, ep.ipv6Bytes());
      }
      if (ep.portAsInt() != 0) {
        insert.set(ZIPKIN_ANNOTATIONS.ENDPOINT_PORT, (short) ep.portAsInt());
      }
    }

    @Override
    public String toString() {
      return "BatchInsertSpansAndAnnotations{spans=" + spans + "}";
    }
  }

  static void updateName(@Nullable String name, TableField<Record, String> column,
    InsertSetMoreStep<Record> insertSpan, Map<TableField<Record, ?>, Object> updateFields) {
    if (name != null && !name.equals("unknown")) {
      insertSpan.set(column, name);
      updateFields.put(column, name);
    } else {
      // old code wrote empty span name
      insertSpan.set(column, "");
    }
  }
}
