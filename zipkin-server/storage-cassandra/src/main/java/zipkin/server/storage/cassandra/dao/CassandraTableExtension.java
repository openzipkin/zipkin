/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra.dao;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import io.micrometer.common.util.StringUtils;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import zipkin.server.storage.cassandra.CQLExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Help to adding the extension tables for the Cassandra table query.
 */
public class CassandraTableExtension {
  public static final String TABLE_TRACE_BY_SERVICE_SPAN = "zipkin_trace_by_service_span";
  public static final String TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE = "zipkin_trace_by_service_remote_service";

  /**
   * Time window covered by a single bucket of the {@link CassandraTableExtension#TABLE_TRACE_BY_SERVICE_SPAN} and
   * {@link CassandraTableExtension#TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE}, in seconds. Default: 1 day
   */
  private static final long DURATION_INDEX_BUCKET_WINDOW_SECONDS =
      Long.getLong("zipkin.store.cassandra.internal.durationIndexBucket", 24 * 60 * 60);

  public static List<CQLExecutor> buildExtensionsForSpan(ZipkinSpanRecord span, SessionCacheCallback callback) {
    long ts_milli = span.getTimestampMillis();
    UUID ts_uuid =
        new UUID(
            Uuids.startOf(ts_milli != 0L ? (ts_milli) : System.currentTimeMillis())
                .getMostSignificantBits(),
            Uuids.random().getLeastSignificantBits());
    int bucket = durationIndexBucket(ts_milli);

    final ArrayList<CQLExecutor> result = new ArrayList<>(3);

    long durationMilli = span.getDuration() / 1000;
    result.add(buildServiceSpan(span.getLocalEndpointServiceName(), span.getName(), bucket, ts_uuid, span.getTraceId(), durationMilli, callback));
    // Allows lookup without the span name)
    result.add(buildServiceSpan(span.getLocalEndpointServiceName(), "", bucket, ts_uuid, span.getTraceId(), durationMilli, callback));

    if (StringUtils.isNotEmpty(span.getRemoteEndpointServiceName())) {
      result.add(buildServiceRemoteService(span.getLocalEndpointServiceName(), span.getRemoteEndpointServiceName(),
          bucket, ts_uuid, span.getTraceId(), callback));
    }

    return result;
  }

  public static int durationIndexBucket(long ts_milli) {
    // if the window constant has microsecond precision, the division produces negative getValues
    return (int) (ts_milli / (DURATION_INDEX_BUCKET_WINDOW_SECONDS)) / 1000;
  }

  private static CQLExecutor buildServiceSpan(String service, String span, int bucket, UUID ts, String trace_id, long durationMillis,
                                              SessionCacheCallback callback) {
    return new CQLExecutor("insert into " + TABLE_TRACE_BY_SERVICE_SPAN +
        " (service, span, bucket, ts, trace_id, duration) values (?, ?, ?, ?, ?, ?)",
        Arrays.asList(service, span, bucket, ts, trace_id, durationMillis), callback, null);
  }

  private static CQLExecutor buildServiceRemoteService(String service, String remoteService, int bucket, UUID ts, String trace_id,
                                              SessionCacheCallback callback) {
    return new CQLExecutor("insert into " + TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE +
        " (service, remote_service, bucket, ts, trace_id) values (?, ?, ?, ?, ?)",
        Arrays.asList(service, remoteService, bucket, ts, trace_id), callback, null);
  }

}
