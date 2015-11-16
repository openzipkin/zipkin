
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.Bytes;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Repository implements AutoCloseable {

    public static final String KEYSPACE = "zipkin";
    public static final short BUCKETS = 10;

    private static final Logger LOG = LoggerFactory.getLogger(Repository.class);
    private static final Random RAND = new Random();

    private static final List<Integer> ALL_BUCKETS = Collections.unmodifiableList(new ArrayList<Integer>() {{
        for (int i = 0; i < BUCKETS; ++i) {
            add(i);
        }
    }});

    private static final long WRITTEN_NAMES_TTL
            = Long.getLong("zipkin.store.cassandra.internal.writtenNamesTtl", 60 * 60 * 1000);

    // Time window covered by a single bucket of the Span Duration Index, in seconds. Default: 1hr
    private static final long DURATION_INDEX_BUCKET_WINDOW_SECONDS
            = Long.getLong("zipkin.store.cassandra.internal.durationIndexBucket", 60 * 60);

    private final Session session;
    private final PreparedStatement selectTraces;
    private final PreparedStatement insertSpan;
    private final PreparedStatement selectDependencies;
    private final PreparedStatement insertDependencies;
    private final PreparedStatement selectServiceNames;
    private final PreparedStatement insertServiceName;
    private final PreparedStatement selectSpanNames;
    private final PreparedStatement insertSpanName;
    private final PreparedStatement selectTraceIdsByServiceName;
    private final PreparedStatement insertTraceIdByServiceName;
    private final PreparedStatement selectTraceIdsBySpanName;
    private final PreparedStatement insertTraceIdBySpanName;
    private final PreparedStatement selectTraceIdsByAnnotations;
    private final PreparedStatement insertTraceIdByAnnotation;
    private final PreparedStatement selectTraceIdsBySpanDuration;
    private final PreparedStatement insertTraceIdBySpanDuration;
    private final Map<String,String> metadata;
    private final ProtocolVersion protocolVersion;

    private final ThreadLocal<Set<String>> writtenNames = new ThreadLocal<Set<String>>() {
            private long cacheInterval = toCacheInterval(System.currentTimeMillis());

            @Override
            protected Set<String> initialValue() {
                return new HashSet<String>();
            }
            @Override
            public Set<String> get() {
                long newCacheInterval = toCacheInterval(System.currentTimeMillis());
                if (cacheInterval != newCacheInterval) {
                    cacheInterval = newCacheInterval;
                    set(new HashSet<String>());
                }
                return super.get();
            }
            private long toCacheInterval(long ms) {
                return ms / WRITTEN_NAMES_TTL;
            }
        };

    /**
     * Note: This constructor performs network I/O to the {@code cluster}.
     */
    public Repository(String keyspace, Cluster cluster, Boolean ensureSchema) {
        if (ensureSchema.booleanValue()) {
            Schema.ensureExists(keyspace, cluster);
        }

        metadata = Schema.readMetadata(keyspace, cluster);
        session = cluster.connect(keyspace);
        protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersionEnum();

        insertSpan = session.prepare(
                QueryBuilder
                    .insertInto("traces")
                    .value("trace_id", QueryBuilder.bindMarker("trace_id"))
                    .value("ts", QueryBuilder.bindMarker("ts"))
                    .value("span_name", QueryBuilder.bindMarker("span_name"))
                    .value("span", QueryBuilder.bindMarker("span"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectTraces = session.prepare(
                QueryBuilder.select("trace_id", "span")
                    .from("traces")
                    .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
                    .limit(QueryBuilder.bindMarker("limit_")));

        selectDependencies = session.prepare(
                QueryBuilder.select("dependencies")
                    .from("dependencies")
                    .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days"))));

        insertDependencies = session.prepare(
                QueryBuilder
                    .insertInto("dependencies")
                    .value("day", QueryBuilder.bindMarker("day"))
                    .value("dependencies", QueryBuilder.bindMarker("dependencies")));

        selectServiceNames = session.prepare(
                QueryBuilder.select("service_name")
                    .from("service_names"));

        insertServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_names")
                    .value("service_name", QueryBuilder.bindMarker("service_name"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectSpanNames = session.prepare(
                QueryBuilder.select("span_name")
                    .from("span_names")
                    .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
                    .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket"))));

        insertSpanName = session.prepare(
                QueryBuilder
                    .insertInto("span_names")
                    .value("service_name", QueryBuilder.bindMarker("service_name"))
                    .value("bucket", QueryBuilder.bindMarker("bucket"))
                    .value("span_name", QueryBuilder.bindMarker("span_name"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectTraceIdsByServiceName = session.prepare(
                QueryBuilder.select("ts", "trace_id")
                    .from("service_name_index")
                    .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
                    .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
                    .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                    .limit(QueryBuilder.bindMarker("limit_"))
                    .orderBy(QueryBuilder.desc("ts")));

        insertTraceIdByServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_name_index")
                    .value("service_name", QueryBuilder.bindMarker("service_name"))
                    .value("bucket", QueryBuilder.bindMarker("bucket"))
                    .value("ts", QueryBuilder.bindMarker("ts"))
                    .value("trace_id", QueryBuilder.bindMarker("trace_id"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectTraceIdsBySpanName = session.prepare(
                QueryBuilder.select("ts", "trace_id")
                    .from("service_span_name_index")
                    .where(QueryBuilder.eq("service_span_name", QueryBuilder.bindMarker("service_span_name")))
                    .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                    .limit(QueryBuilder.bindMarker("limit_"))
                    .orderBy(QueryBuilder.desc("ts")));

        insertTraceIdBySpanName = session.prepare(
                QueryBuilder
                    .insertInto("service_span_name_index")
                    .value("service_span_name", QueryBuilder.bindMarker("service_span_name"))
                    .value("ts", QueryBuilder.bindMarker("ts"))
                    .value("trace_id", QueryBuilder.bindMarker("trace_id"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectTraceIdsByAnnotations = session.prepare(
                QueryBuilder.select("ts", "trace_id")
                    .from("annotations_index")
                    .where(QueryBuilder.eq("annotation", QueryBuilder.bindMarker("annotation")))
                    .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
                    .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                    .limit(QueryBuilder.bindMarker("limit_"))
                    .orderBy(QueryBuilder.desc("ts")));

        insertTraceIdByAnnotation = session.prepare(
                QueryBuilder
                    .insertInto("annotations_index")
                    .value("annotation", QueryBuilder.bindMarker("annotation"))
                    .value("bucket", QueryBuilder.bindMarker("bucket"))
                    .value("ts", QueryBuilder.bindMarker("ts"))
                    .value("trace_id", QueryBuilder.bindMarker("trace_id"))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

        selectTraceIdsBySpanDuration = session.prepare(
                QueryBuilder.select("d", "ts", "tid")
                    .from("span_duration_index")
                    .where(QueryBuilder.eq("s", QueryBuilder.bindMarker("service_name")))
                        .and(QueryBuilder.eq("sp", QueryBuilder.bindMarker("span_name")))
                        .and(QueryBuilder.eq("b", QueryBuilder.bindMarker("time_bucket")))
                        .and(QueryBuilder.lte("d", QueryBuilder.bindMarker("max_duration")))
                        .and(QueryBuilder.gte("d", QueryBuilder.bindMarker("min_duration")))
                    .orderBy(QueryBuilder.desc("d")));

        insertTraceIdBySpanDuration = session.prepare(
                QueryBuilder
                        .insertInto("span_duration_index")
                        .value("s", QueryBuilder.bindMarker("service_name"))
                        .value("sp", QueryBuilder.bindMarker("span_name"))
                        .value("b", QueryBuilder.bindMarker("bucket"))
                        .value("d", QueryBuilder.bindMarker("duration"))
                        .value("ts", QueryBuilder.bindMarker("ts"))
                        .value("tid", QueryBuilder.bindMarker("trace_id"))
                        .using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_"))));

    }

    /**
     * Store the span in the underlying storage for later retrieval.
     */
    public ListenableFuture<Void> storeSpan(long traceId, long timestamp, String spanName, ByteBuffer span, int ttl) {
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());

        try {
            if (0 == timestamp && metadata.get("traces.compaction.class").contains("DateTieredCompactionStrategy")) {
                LOG.warn("span with no first or last timestamp. "
                        + "if this happens a lot consider switching back to SizeTieredCompactionStrategy for "
                        + KEYSPACE + ".traces");
            }

            BoundStatement bound = insertSpan.bind()
                    .setLong("trace_id", traceId)
                    .setBytesUnsafe("ts", serializeTs(timestamp))
                    .setString("span_name", spanName)
                    .setBytes("span", span)
                    .setInt("ttl_", ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertSpan(traceId, timestamp, spanName, span, ttl));
            }

            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertSpan(traceId, timestamp, spanName, span, ttl), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertSpan(long traceId, long timestamp, String spanName, ByteBuffer span, int ttl) {
        return insertSpan.getQueryString()
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ts", String.valueOf(timestamp))
                .replace(":span_name", spanName)
                .replace(":span", Bytes.toHexString(span))
                .replace(":ttl_", String.valueOf(ttl));
    }

    /**
     * Get the available trace information from the storage system.
     * Spans in trace should be sorted by the first annotation timestamp
     * in that span. First event should be first in the spans list.
     * <p>
     * The return list will contain only spans that have been found, thus
     * the return list may not match the provided list of ids.
     */
    public ListenableFuture<Map<Long, List<ByteBuffer>>> getSpansByTraceIds(Long[] traceIds, int limit) {
        Preconditions.checkNotNull(traceIds);
        try {
            if (0 < traceIds.length) {

                BoundStatement bound = selectTraces.bind()
                        .setList("trace_id", Arrays.asList(traceIds))
                        .setInt("limit_", limit);

                bound.setFetchSize(Integer.MAX_VALUE);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectTraces(traceIds, limit));
                }

                return Futures.transform(
                    session.executeAsync(bound),
                    new Function<ResultSet, Map<Long, List<ByteBuffer>>>() {

                        @Override
                        public Map<Long, List<ByteBuffer>> apply(ResultSet input) {
                            Map<Long, List<ByteBuffer>> spans = new LinkedHashMap<>();

                            for (Row row : input) {
                                long traceId = row.getLong("trace_id");
                                if (!spans.containsKey(traceId)) {
                                    spans.put(traceId, new ArrayList<ByteBuffer>());
                                }
                                spans.get(traceId).add(row.getBytes("span"));
                            }

                            return spans;
                        }
                    }
                );

            } else {
                return Futures.immediateFuture(Collections.<Long, List<ByteBuffer>>emptyMap());
            }
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraces(traceIds, limit), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugSelectTraces(Long[] traceIds, int limit) {
        return selectTraces.getQueryString()
                        .replace(":trace_id", Arrays.toString(traceIds))
                        .replace(":limit_", String.valueOf(limit));
    }

    public ListenableFuture<Void> storeDependencies(long epochDayMillis, ByteBuffer dependencies) {
        Date startFlooredToDay = new Date(epochDayMillis);
        try {
            BoundStatement bound = insertDependencies.bind()
                    .setDate("day", startFlooredToDay)
                    .setBytes("dependencies", dependencies);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertDependencies(startFlooredToDay, dependencies));
            }
            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertDependencies(startFlooredToDay, dependencies), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertDependencies(Date startFlooredToDay, ByteBuffer dependencies) {
        return insertDependencies.getQueryString()
                        .replace(":day", startFlooredToDay.toString())
                        .replace(":dependencies", Bytes.toHexString(dependencies));
    }

    public ListenableFuture<List<ByteBuffer>> getDependencies(long startEpochDayMillis, long endEpochDayMillis) {
        List<Date> days = getDays(startEpochDayMillis, endEpochDayMillis);
        try {
            BoundStatement bound = selectDependencies.bind().setList("days", days);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectDependencies(days));
            }
            return Futures.transform(
                session.executeAsync(bound),
                new Function<ResultSet, List<ByteBuffer>>() {
                    @Override
                    public List<ByteBuffer> apply(ResultSet input) {
                        List<ByteBuffer> dependencies = new ArrayList<>();
                        for (Row row : input) {
                            dependencies.add(row.getBytes("dependencies"));
                        }
                        return dependencies;
                    }
                }
            );
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectDependencies(days), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugSelectDependencies(List<Date> days) {
        return selectDependencies.getQueryString().replace(":days", Arrays.toString(days.toArray()));
    }

    public ListenableFuture<Set<String>> getServiceNames() {
        try {
            BoundStatement bound = selectServiceNames.bind();
            if (LOG.isDebugEnabled()) {
                LOG.debug(selectServiceNames.getQueryString());
            }

            return Futures.transform(
              session.executeAsync(bound),
              new Function<ResultSet, Set<String>>() {
                  @Override
                  public Set<String> apply(ResultSet input) {
                      Set<String> serviceNames = new HashSet<>();
                      for (Row row : input) {
                          serviceNames.add(row.getString("service_name"));
                      }
                      return serviceNames;
                  }
              }
            );
        } catch (RuntimeException ex) {
            LOG.error("failed " + selectServiceNames.getQueryString(), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    public ListenableFuture<Void> storeServiceName(String serviceName, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        if (writtenNames.get().add(serviceName)) {
            try {
                BoundStatement bound = insertServiceName.bind()
                        .setString("service_name", serviceName)
                        .setInt("ttl_", ttl);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugInsertServiceName(serviceName, ttl));
                }

                return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
            } catch (RuntimeException ex) {
                LOG.error("failed " + debugInsertServiceName(serviceName, ttl), ex);
                writtenNames.get().remove(serviceName);
                throw ex;
            }
        } else {
            return Futures.immediateFuture(null);
        }
    }

    private String debugInsertServiceName(String serviceName, int ttl) {
        return insertServiceName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":ttl_", String.valueOf(ttl));
    }

    public ListenableFuture<Set<String>> getSpanNames(String serviceName) {
        Preconditions.checkNotNull(serviceName);
        serviceName = serviceName.toLowerCase(); // service names are always lowercase!
        try {
            if (!serviceName.isEmpty()) {

                BoundStatement bound = selectSpanNames.bind()
                        .setString("service_name", serviceName)
                        .setInt("bucket", 0);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectSpanNames(serviceName));
                }

                return Futures.transform(
                    session.executeAsync(bound),
                    new Function<ResultSet, Set<String>>() {
                        @Override
                        public Set<String> apply(ResultSet input) {
                            Set<String> spanNames = new HashSet<>();
                            for (Row row : input) {
                                spanNames.add(row.getString("span_name"));
                            }
                            return spanNames;
                        }
                    }
                );
            } else {
                return Futures.immediateFuture(Collections.<String>emptySet());
            }
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectSpanNames(serviceName), ex);
            throw ex;
        }
    }

    private String debugSelectSpanNames(String serviceName) {
        return selectSpanNames.getQueryString().replace(':' + "service_name", serviceName);
    }

    public ListenableFuture<Void> storeSpanName(String serviceName, String spanName, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        if (writtenNames.get().add(serviceName + "––" + spanName)) {
            try {
                BoundStatement bound = insertSpanName.bind()
                        .setString("service_name", serviceName)
                        .setInt("bucket", 0)
                        .setString("span_name", spanName)
                        .setInt("ttl_", ttl);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugInsertSpanName(serviceName, spanName, ttl));
                }

                return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
            } catch (RuntimeException ex) {
                LOG.error("failed " + debugInsertSpanName(serviceName, spanName, ttl), ex);
                writtenNames.get().remove(serviceName + "––" + spanName);
                return Futures.immediateFailedFuture(ex);
            }
        } else {
            return Futures.immediateFuture(null);
        }
    }

    private String debugInsertSpanName(String serviceName, String spanName, int ttl) {
        return insertSpanName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":span_name", spanName)
                .replace(":ttl_", String.valueOf(ttl));
    }

    public ListenableFuture<Map<Long,Long>> getTraceIdsByServiceName(String serviceName, long endTs, long lookback, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        long startTs = endTs - lookback;
        try {
            BoundStatement bound = selectTraceIdsByServiceName.bind()
                    .setString("service_name", serviceName)
                    .setList("bucket", ALL_BUCKETS)
                    .setBytesUnsafe("start_ts", serializeTs(startTs))
                    .setBytesUnsafe("end_ts", serializeTs(endTs))
                    .setInt("limit_", limit);

            bound.setFetchSize(Integer.MAX_VALUE);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByServiceName(serviceName, startTs, endTs, limit));
            }

            return Futures.transform(
                session.executeAsync(bound),
                new Function<ResultSet, Map<Long, Long>>() {
                    @Override
                    public Map<Long, Long> apply(ResultSet input) {
                        Map<Long,Long> traceIdsToTimestamps = new LinkedHashMap<>();
                        for (Row row : input) {
                            traceIdsToTimestamps.put(row.getLong("trace_id"), deserializeTs(row, "ts"));
                        }
                        return traceIdsToTimestamps;
                    }
                }
            );
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByServiceName(serviceName, startTs, endTs, limit), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugSelectTraceIdsByServiceName(String serviceName, long startTs, long endTs, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":start_ts", new Date(startTs / 1000).toString())
                .replace(":end_ts", new Date(endTs / 1000).toString())
                .replace(":limit_", String.valueOf(limit));
    }

    public ListenableFuture<Void> storeTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {

        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {

            BoundStatement bound = insertTraceIdByServiceName.bind()
                    .setString("service_name", serviceName)
                    .setInt("bucket", RAND.nextInt(BUCKETS))
                    .setBytesUnsafe("ts", serializeTs(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdByServiceName(serviceName, timestamp, traceId, ttl));
            }

            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdByServiceName(serviceName, timestamp, traceId, ttl), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {
        return insertTraceIdByServiceName.getQueryString()
                        .replace(":service_name", serviceName)
                        .replace(":ts", new Date(timestamp / 1000).toString())
                        .replace(":trace_id", new Date(traceId).toString())
                        .replace(":ttl_", String.valueOf(ttl));
    }

    public ListenableFuture<Map<Long,Long>> getTraceIdsBySpanName(String serviceName, String spanName, long endTs, long lookback, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        String serviceSpanName = serviceName + "." + spanName;
        long startTs = endTs - lookback;
        try {
            BoundStatement bound = selectTraceIdsBySpanName.bind()
                    .setString("service_span_name", serviceSpanName)
                    .setBytesUnsafe("start_ts", serializeTs(startTs))
                    .setBytesUnsafe("end_ts", serializeTs(endTs))
                    .setInt("limit_", limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsBySpanName(serviceSpanName, startTs, endTs, limit));
            }

            return Futures.transform(
                session.executeAsync(bound),
                new Function<ResultSet, Map<Long, Long>>() {
                    @Override
                    public Map<Long, Long> apply(ResultSet input) {
                        Map<Long,Long> traceIdsToTimestamps = new LinkedHashMap<>();
                        for (Row row : input) {
                            traceIdsToTimestamps.put(row.getLong("trace_id"), deserializeTs(row, "ts"));
                        }
                        return traceIdsToTimestamps;
                    }
                }
            );

        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsBySpanName(serviceSpanName, startTs, endTs, limit), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugSelectTraceIdsBySpanName(String serviceSpanName, long startTs, long endTs, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(":service_span_name", serviceSpanName)
                .replace(":start_ts", new Date(startTs / 1000).toString())
                .replace(":end_ts", new Date(endTs / 1000).toString())
                .replace(":limit_", String.valueOf(limit));
    }

    public ListenableFuture<Void> storeTraceIdBySpanName(String serviceName, String spanName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            String serviceSpanName = serviceName + "." + spanName;

            BoundStatement bound = insertTraceIdBySpanName.bind()
                    .setString("service_span_name", serviceSpanName)
                    .setBytesUnsafe("ts", serializeTs(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdBySpanName(serviceSpanName, timestamp, traceId, ttl));
            }
            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdBySpanName(serviceName, timestamp, traceId, ttl), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertTraceIdBySpanName(String serviceSpanName, long timestamp, long traceId, int ttl) {
        return insertTraceIdBySpanName.getQueryString()
                .replace(":service_span_name", serviceSpanName)
                .replace(":ts", String.valueOf(timestamp))
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ttl_", String.valueOf(ttl));
    }

    public ListenableFuture<Map<Long,Long>> getTraceIdsByAnnotation(ByteBuffer annotationKey, long endTs, long lookback, int limit) {
        long startTs = endTs - lookback;
        try {
            BoundStatement bound = selectTraceIdsByAnnotations.bind()
                    .setBytes("annotation", annotationKey)
                    .setList("bucket", ALL_BUCKETS)
                    .setBytesUnsafe("start_ts", serializeTs(startTs))
                    .setBytesUnsafe("end_ts", serializeTs(endTs))
                    .setInt("limit_", limit);

            bound.setFetchSize(Integer.MAX_VALUE);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByAnnotations(annotationKey, startTs, endTs, limit));
            }

            return Futures.transform(
              session.executeAsync(bound),
              new Function<ResultSet, Map<Long, Long>>() {
                  @Override
                  public Map<Long, Long> apply(ResultSet input) {
                      Map < Long, Long > traceIdsToTimestamps = new LinkedHashMap<>();
                      for (Row row : input) {
                          traceIdsToTimestamps.put(row.getLong("trace_id"), deserializeTs(row, "ts"));
                      }
                      return traceIdsToTimestamps;
                  }
              }
            );
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByAnnotations(annotationKey, startTs, endTs, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByAnnotations(ByteBuffer annotationKey, long startTs, long endTs, int limit) {
            return selectTraceIdsByAnnotations.getQueryString()
                            .replace(":annotation", new String(Bytes.getArray(annotationKey)))
                            .replace(":start_ts", new Date(startTs / 1000).toString())
                            .replace(":end_ts", new Date(endTs / 1000).toString())
                            .replace(":limit_", String.valueOf(limit));
    }

    public ListenableFuture<Void> storeTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        try {
            BoundStatement bound = insertTraceIdByAnnotation.bind()
                    .setBytes("annotation", annotationKey)
                    .setInt("bucket", RAND.nextInt(BUCKETS))
                    .setBytesUnsafe("ts", serializeTs(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdByAnnotation(annotationKey, timestamp, traceId, ttl));
            }
            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdByAnnotation(annotationKey, timestamp, traceId, ttl), ex);
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        return insertTraceIdByAnnotation.getQueryString()
                .replace(":annotation", new String(Bytes.getArray(annotationKey)))
                .replace(":ts", new Date(timestamp / 1000).toString())
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ttl_", String.valueOf(ttl));
    }

    private class DurationRow {
        Long trace_id;
        Long duration;
        Long timestamp;
        DurationRow(Row row) {
            trace_id = row.getLong("tid");
            duration = row.getLong("d");
            timestamp = row.getLong("ts");
        }
        public String toString() {
            return String.format("trace_id=%d, duration=%d, timestamp=%d", trace_id, duration, timestamp);
        }
    }

    /** Returns a map of trace id to timestamp */
    public ListenableFuture<Map<Long, Long>> getTraceIdsByDuration(String serviceName, String spanName,
                                                                   long minDuration, long maxDuration,
                                                                   long endTs, long startTs, int limit) {
        int startBucket = durationIndexBucket(startTs);
        int endBucket = durationIndexBucket(endTs);
        try {
            if (startBucket > endBucket) {
                throw new IllegalArgumentException("Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
            }
            IntFunction<ListenableFuture<List<DurationRow>>> oneBucketQuery = (bucket) -> {
                BoundStatement bound = selectTraceIdsBySpanDuration.bind()
                        .setString("service_name", serviceName)
                        .setString("span_name", spanName == null ? "" : spanName)
                        .setInt("time_bucket", bucket)
                        .setLong("max_duration", maxDuration)
                        .setLong("min_duration", minDuration);
                // optimistically setting fetch size to 'limit' here. Since we are likely to filter some results
                // because their timestamps are out of range, we may need to fetch again.
                // TODO figure out better strategy
                bound.setFetchSize(limit);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectTraceIdsByDuration(serviceName, spanName, minDuration, maxDuration, limit));
                }
                return Futures.transform(
                        session.executeAsync(bound),
                        (ResultSet rs) -> {
                            Iterable<Row> it = rs::iterator;
                            return StreamSupport
                                    .stream(it.spliterator(), false)
                                    .map(DurationRow::new)
                                    .filter((row) -> row.timestamp >= startTs && row.timestamp <= endTs)
                                    .limit(limit)
                                    .collect(Collectors.toList());
                        }
                );
            };

            List<ListenableFuture<List<DurationRow>>> futures = IntStream
                    .rangeClosed(startBucket, endBucket)
                    .mapToObj(oneBucketQuery)
                    .collect(Collectors.toList());

            return Futures.transform(Futures.successfulAsList(futures),
                    (List<List<DurationRow>> input) -> {
                        return input.stream()
                                .flatMap(Collection::stream)
                                .collect( // bloody IntelliJ can't infer types
                                        Collectors.groupingBy((DurationRow d) -> d.trace_id,
                                                Collectors.collectingAndThen(
                                                        // find earliest startTs for each trace ID
                                                        Collectors.minBy((d1, d2) -> d1.timestamp.compareTo(d2.timestamp)),
                                                        // convert from Optional to Long - we always have at least 1 value
                                                        (Optional<DurationRow> d) -> d.get().timestamp)));
                    });
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByDuration(serviceName, spanName, minDuration, maxDuration, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByDuration(String serviceName, String spanName, long minDuration, long maxDuration, int limit) {
        return selectTraceIdsBySpanDuration.getQueryString()
            .replace(":service_name", serviceName)
            .replace(":span_name", spanName)
            .replace(":max_duration", String.valueOf(maxDuration))
            .replace(":min_duration", String.valueOf(minDuration))
            .replace(":limit_", String.valueOf(limit));
    }

    private int durationIndexBucket(long ts) {
        // if the window constant has microsecond precision, the division produces negative values
        return (int)((ts / DURATION_INDEX_BUCKET_WINDOW_SECONDS) / 1000000);
    }

    public ListenableFuture<Void> storeTraceIdByDuration(String serviceName, String spanName, long timestamp, long duration,
                                                         long traceId, int ttl) {
        try {
            BoundStatement bound = insertTraceIdBySpanDuration.bind()
                .setString("service_name", serviceName)
                .setString("span_name", spanName)
                .setInt("bucket", durationIndexBucket(timestamp))
                .setLong("ts", timestamp)
                .setLong("duration", duration)
                .setLong("trace_id", traceId)
                .setInt("ttl_", ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdBySpanDuration(serviceName, spanName, timestamp, duration, traceId, ttl));
            }
            return Futures.transform(session.executeAsync(bound), resultSetToVoidFunction);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdBySpanDuration(serviceName, spanName, timestamp, duration, traceId, ttl));
            return Futures.immediateFailedFuture(ex);
        }
    }

    private String debugInsertTraceIdBySpanDuration(String serviceName, String spanName, long timestamp, long duration,
                                                    long traceId, int ttl) {
        return insertTraceIdBySpanDuration.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":span_name", spanName)
                .replace(":bucket", String.valueOf(durationIndexBucket(timestamp)))
                .replace(":ts", new Date(timestamp / 1000).toString())
                .replace(":duration", String.valueOf(duration))
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ttl_", String.valueOf(ttl));
    }

    private static List<Date> getDays(long from, long to) {
        List<Date> days = new ArrayList<>();
        for (long time = from; time <= to; time += TimeUnit.DAYS.toMillis(1)) {
            days.add(new Date(time));
        }
        return days;
    }

    @Override
    public void close() {
        session.close();
    }

    private static class Schema {

        private static final String SCHEMA = "/cassandra-schema-cql3.txt";

        static Map<String, String> readMetadata(String keyspace, Cluster cluster) {
            Map<String, String> metadata = new LinkedHashMap<>();
            try (Session session = cluster.connect()) {
                KeyspaceMetadata keyspaceMetadata = getKeyspaceMetadata(keyspace, cluster);

                Map<String, String> replicatn = keyspaceMetadata.getReplication();
                if ("SimpleStrategy".equals(replicatn.get("class")) && "1".equals(replicatn.get("replication_factor"))) {
                    LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
                }
                Map<String, String> tracesCompaction = keyspaceMetadata.getTable("traces").getOptions().getCompaction();
                metadata.put("traces.compaction.class", tracesCompaction.get("class"));
            }

            return metadata;
        }

        private static KeyspaceMetadata getKeyspaceMetadata(String keyspace, Cluster cluster) {
            KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);

            if (keyspaceMetadata == null) {
                throw new IllegalStateException(String.format(
                        "Cannot read keyspace metadata for give keyspace: %s and cluster: %s",
                        keyspace, cluster.getClusterName()));
            }
            return keyspaceMetadata;
        }

        static void ensureExists(String keyspace, Cluster cluster) {
            try (Session session = cluster.connect()) {
                try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(SCHEMA))) {
                    for (String cmd : String.format(CharStreams.toString(reader)).split(";")) {
                        cmd = cmd.trim().replace(" " + KEYSPACE, " " + keyspace);
                        if (!cmd.isEmpty()) {
                            session.execute(cmd);
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

        private Schema() {}
    }

    private Function<ResultSet, Void> resultSetToVoidFunction = input -> null;

    // Overrides default codec of timestamps as dates (as doing so truncates to millis).
    // TODO: When we switch to datastax java-driver v3+, move this to a custom codec.
    private ByteBuffer serializeTs(long timestamp) {
        return DataType.bigint().serialize(timestamp, protocolVersion);
    }

    private long deserializeTs(Row row, String name) {
        return (long) DataType.bigint().deserialize(row.getBytesUnsafe(name), protocolVersion);
    }
}
