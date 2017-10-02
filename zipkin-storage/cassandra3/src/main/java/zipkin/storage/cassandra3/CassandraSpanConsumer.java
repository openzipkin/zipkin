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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;
import zipkin.storage.cassandra3.Schema.AnnotationUDT;
import zipkin.storage.cassandra3.Schema.EndpointUDT;

import static zipkin.storage.cassandra3.CassandraUtil.bindWithName;
import static zipkin.storage.cassandra3.CassandraUtil.durationIndexBucket;

final class CassandraSpanConsumer implements SpanConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanConsumer.class);

  private final Session session;
  private final boolean strictTraceId;
  private final PreparedStatement insertSpan;
  private final PreparedStatement insertTraceServiceSpanName;
  private final PreparedStatement insertServiceSpanName;

  CassandraSpanConsumer(Session session, boolean strictTraceId) {
    this.session = session;
    this.strictTraceId = strictTraceId;
    Schema.readMetadata(session);

    insertSpan = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_TRACES)
            .value("trace_id", QueryBuilder.bindMarker("trace_id"))
            .value("ts_uuid", QueryBuilder.bindMarker("ts_uuid"))
            .value("id", QueryBuilder.bindMarker("id"))
            .value("ts", QueryBuilder.bindMarker("ts"))
            .value("span", QueryBuilder.bindMarker("span"))
            .value("parent_id", QueryBuilder.bindMarker("parent_id"))
            .value("duration", QueryBuilder.bindMarker("duration"))
            .value("l_ep", QueryBuilder.bindMarker("l_ep"))
            .value("l_service", QueryBuilder.bindMarker("l_service"))
            .value("r_ep", QueryBuilder.bindMarker("r_ep"))
            .value("annotations", QueryBuilder.bindMarker("annotations"))
            .value("tags", QueryBuilder.bindMarker("tags"))
            .value("shared", QueryBuilder.bindMarker("shared"))
            .value("annotation_query", QueryBuilder.bindMarker("annotation_query")));

    insertTraceServiceSpanName = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_TRACE_BY_SERVICE_SPAN)
            .value("service", QueryBuilder.bindMarker("service"))
            .value("span", QueryBuilder.bindMarker("span"))
            .value("bucket", QueryBuilder.bindMarker("bucket"))
            .value("ts", QueryBuilder.bindMarker("ts"))
            .value("trace_id", QueryBuilder.bindMarker("trace_id"))
            .value("duration", QueryBuilder.bindMarker("duration")));

    insertServiceSpanName = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_SERVICE_SPANS)
            .value("service", QueryBuilder.bindMarker("service"))
            .value("span", QueryBuilder.bindMarker("span")));
  }

  /**
   * This fans out into many requests, last count was 2 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override
  public Call<Void> accept(List<Span> spans) {
    for (Span s : spans) {
      // indexing occurs by timestamp, so derive one if not present.
      long timestamp = s.timestamp() != null ? s.timestamp() : guessTimestamp(s);
      storeSpan(s, timestamp);

      // Contract for Repository.storeTraceServiceSpanName is to store the span twice, once with
      // the span name and another with empty string.
      storeTraceServiceSpanName(s.localServiceName(), s.name(), timestamp, s.duration(), s.traceId());
      storeTraceServiceSpanName(s.remoteServiceName(), s.name(), timestamp, s.duration(), s.traceId());
      if (null != s.name()) { // If span.name is null, this would be redundant
        storeTraceServiceSpanName(s.localServiceName(), "", timestamp, s.duration(), s.traceId());
        storeTraceServiceSpanName(s.remoteServiceName(), "", timestamp, s.duration(), s.traceId());
      }
      storeServiceSpanName(s.localServiceName(), s.name());
      storeServiceSpanName(s.remoteServiceName(), s.name());
    }
    return Call.create(null /* Void == null */);
  }

  /**
   * Store the span in the underlying storage for later retrieval.
   */
  void storeSpan(Span span, long timestamp) {
    try {
      List<AnnotationUDT> annotations = span.annotations().stream()
              .map(a -> new AnnotationUDT(a))
              .collect(Collectors.toList());

      BoundStatement bound = bindWithName(insertSpan, "insert-span")
          .setString("trace_id", span.traceId())
          .setUUID("ts_uuid", new UUID(
              UUIDs.startOf(timestamp / 1000).getMostSignificantBits(),
              UUIDs.random().getLeastSignificantBits()))
          .setString("id", span.id())
          .setString("span", span.name())
          .setList("annotations", annotations)
          .setMap("tags", span.tags())
          .setString("annotation_query", Joiner.on(',').join(CassandraUtil.annotationKeys(span)));

      if (null != span.timestamp()) {
        bound = bound.setLong("ts", span.timestamp());
      }
      if (null != span.duration()) {
        bound = bound.setLong("duration", span.duration());
      }
      if (null != span.parentId()) {
        bound = bound.setString("parent_id", span.parentId());
      }
      if (null != span.localEndpoint()) {
        bound = bound
                .set("l_ep", new EndpointUDT(span.localEndpoint()), EndpointUDT.class)
                .setString("l_service", span.localServiceName());
      }
      if (null != span.remoteEndpoint()) {
        bound = bound.set("r_ep", new EndpointUDT(span.remoteEndpoint()), EndpointUDT.class);
      }
      if (null != span.shared()) {
        bound = bound.setBool("shared", span.shared());
      }

      if (!strictTraceId && span.traceId().length() == 32) {
          // store the span twice, once for 128-bit ID and once for the lower 64 bits
          storeSpan(span.toBuilder().traceId(span.traceId().substring(16)).build(), timestamp);
      }
      session.executeAsync(bound);
    } catch (RuntimeException ignore) {
      LOG.error(ignore.getMessage(), ignore);
    }
  }

  void storeTraceServiceSpanName(
      String serviceName,
      String spanName,
      long timestamp_micro,
      Long duration,
      String traceId) {

    int bucket = durationIndexBucket(timestamp_micro);
    UUID ts = new UUID(
        UUIDs.startOf(timestamp_micro / 1000).getMostSignificantBits(),
        UUIDs.random().getLeastSignificantBits());
    try {
      BoundStatement bound =
          bindWithName(insertTraceServiceSpanName, "insert-trace-service-span-name")
              .setString("service", serviceName)
              .setString("span", spanName)
              .setInt("bucket", bucket)
              .setUUID("ts", ts)
              .setString("trace_id", traceId);

      if (null != duration) {
        bound = bound.setLong("duration", duration);
      }
      session.executeAsync(bound);
    } catch (RuntimeException ignore) {
      LOG.error(ignore.getMessage(), ignore);
    }
  }

  void storeServiceSpanName(String serviceName, String spanName) {
    try {
      BoundStatement bound = bindWithName(insertServiceSpanName, "insert-service-span-name")
          .setString("service", serviceName)
          .setString("span", spanName);

      session.executeAsync(bound);
    } catch (RuntimeException ignore) {
      LOG.error(ignore.getMessage(), ignore);
    }
  }

  private static long guessTimestamp(Span span) {
    Preconditions.checkState(null == span.timestamp(), "method only for when span has no timestamp");
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) {
        return annotation.timestamp();
      }
    }
    return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
  }
}
