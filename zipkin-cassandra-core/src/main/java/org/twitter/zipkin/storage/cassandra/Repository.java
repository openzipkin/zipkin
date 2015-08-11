
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.Bytes;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Repository implements AutoCloseable {

    static final String KEYSPACE = "zipkin";

    private static final String KEY_MARKER = "k";
    private static final String COLUMN_MARKER = "c";
    private static final String VALUE_MARKER = "v";
    private static final String TTL_MARKER = "t";
    private static final String LIMIT_MARKER = "l";

    private static final Logger LOG = LoggerFactory.getLogger(Repository.class);

    private final Session session;
    private final PreparedStatement selectTraces;
    private final PreparedStatement selectTraceTtl;
    private final PreparedStatement insertSpan;
    private final PreparedStatement selectTopAnnotations;
    private final PreparedStatement deleteTopAnnotations;
    private final PreparedStatement insertTopAnnotations;
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
    private final PreparedStatement insertTraceDurations;
    private final PreparedStatement selectTraceDurationHead;
    private final PreparedStatement selectTraceDurationTail;


    public Repository(String keyspace, Cluster cluster) {
        Schema.ensureExists(keyspace, cluster);
        session = cluster.connect(keyspace);

        // @fixme use cassandraSpanStore.cfs.traces (from ZipkinColumnFamilyNames)
        //        or "traces" is replaced with a prepared statement being passed in
        insertSpan = session.prepare(
                QueryBuilder
                    .insertInto("traces")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraces = session.prepare(
                QueryBuilder.select("key", "value")
                    .from("traces")
                    .where(QueryBuilder.in("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER)));

        selectTraceTtl = session.prepare(
                QueryBuilder.select()
                    .ttl("value")
                    .from("traces")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER))));

        selectTopAnnotations = session.prepare(
                QueryBuilder.select("value")
                    .from("top_annotations")
                    .where(QueryBuilder.in("key", QueryBuilder.bindMarker(KEY_MARKER))));

        deleteTopAnnotations = session.prepare(
                QueryBuilder
                    .delete()
                    .from("top_annotations")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER))));

        insertTopAnnotations = session.prepare(
                QueryBuilder
                    .insertInto("top_annotations")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER)));

        selectDependencies = session.prepare(
                QueryBuilder.select("value")
                    .from("dependencies")
                    .where(QueryBuilder.in("key", QueryBuilder.bindMarker(KEY_MARKER))));

        insertDependencies = session.prepare(
                QueryBuilder
                    .insertInto("dependencies")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER)));

        selectServiceNames = session.prepare(
                QueryBuilder.select("column1")
                    .from("service_names"));

        insertServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_names")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectSpanNames = session.prepare(
                QueryBuilder.select("column1")
                    .from("span_names")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER))));

        insertSpanName = session.prepare(
                QueryBuilder
                    .insertInto("span_names")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsByServiceName = session.prepare(
                QueryBuilder.select("column1", "value")
                    .from("service_name_index")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .and(QueryBuilder.lte("column1", QueryBuilder.bindMarker(COLUMN_MARKER)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                     // @todo use CLUSTERING ORDER instead
                    .orderBy(QueryBuilder.desc("column1")));

        insertTraceIdByServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_name_index")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsBySpanName = session.prepare(
                QueryBuilder.select("column1", "value")
                    .from("service_span_name_index")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .and(QueryBuilder.lte("column1", QueryBuilder.bindMarker(COLUMN_MARKER)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                     // @todo use CLUSTERING ORDER instead
                    .orderBy(QueryBuilder.desc("column1")));

        insertTraceIdBySpanName = session.prepare(
                QueryBuilder
                    .insertInto("service_span_name_index")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsByAnnotations = session.prepare(
                QueryBuilder.select("column1", "value")
                    .from("annotations_index")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .and(QueryBuilder.lte("column1", QueryBuilder.bindMarker(COLUMN_MARKER)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                     // @todo use CLUSTERING ORDER instead
                    .orderBy(QueryBuilder.desc("column1")));

        insertTraceIdByAnnotation = session.prepare(
                QueryBuilder
                    .insertInto("annotations_index")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceDurationHead = session.prepare(
                QueryBuilder.select("key", "column1")
                    .from("duration_index")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .limit(1)
                    .orderBy(QueryBuilder.asc("column1")));

        selectTraceDurationTail = session.prepare(
                QueryBuilder.select("key", "column1")
                    .from("duration_index")
                    .where(QueryBuilder.eq("key", QueryBuilder.bindMarker(KEY_MARKER)))
                    .limit(1)
                    .orderBy(QueryBuilder.desc("column1")));

        insertTraceDurations = session.prepare(
                QueryBuilder
                    .insertInto("duration_index")
                    .value("key", QueryBuilder.bindMarker(KEY_MARKER))
                    .value("column1", QueryBuilder.bindMarker(COLUMN_MARKER))
                    .value("value", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));
    }

    /**
     * Store the span in the underlying storage for later retrieval.
     * @return a future for the operation
     */
    public void storeSpan(long traceId, String spanName, ByteBuffer span, int ttl) {
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            ByteBuffer key = ByteBuffer.allocate(8).putLong(traceId);
            key.rewind();

            BoundStatement bound = insertSpan.bind()
                    .setBytes(KEY_MARKER, key)
                    .setBytes(COLUMN_MARKER, ByteBuffer.wrap(spanName.getBytes()))
                    .setBytes(VALUE_MARKER, span)
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertSpan(traceId, spanName, span, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertSpan(traceId, spanName, span, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertSpan(long traceId, String spanName, ByteBuffer span, int ttl) {
        return insertSpan.getQueryString()
                .replace(':' + KEY_MARKER, String.valueOf(traceId))
                .replace(':' + COLUMN_MARKER, spanName)
                .replace(':' + VALUE_MARKER, Bytes.toHexString(span))
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Set<Long> tracesExist(Long[] traceIds) {
        Preconditions.checkNotNull(traceIds);
        Preconditions.checkArgument(0 < traceIds.length);
        return getSpansByTraceIds(traceIds, 100000).keySet();
    }

    /**
     * Get the available trace information from the storage system.
     * Spans in trace should be sorted by the first annotation timestamp
     * in that span. First event should be first in the spans list.
     *
     * The return list will contain only spans that have been found, thus
     * the return list may not match the provided list of ids.
     */
    public Map<Long,List<ByteBuffer>> getSpansByTraceIds(Long[] traceIds, int limit) {
        Preconditions.checkNotNull(traceIds);
        Preconditions.checkArgument(0 < traceIds.length);
        try {
            List<ByteBuffer> keys = new ArrayList<>();
            for (Long traceId : traceIds) {
                assert null != traceId;
                ByteBuffer key = ByteBuffer.allocate(8).putLong(traceId);
                key.rewind();
                keys.add(key);
            }
            BoundStatement bound = selectTraces.bind().setList(KEY_MARKER, keys).setInt(LIMIT_MARKER, limit);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraces(traceIds, limit));
            }
            Map<Long,List<ByteBuffer>> spans = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                long traceId = row.getBytes("key").getLong();
                if (!spans.containsKey(traceId)) {
                    spans.put(traceId, new ArrayList<ByteBuffer>());
                }
                spans.get(traceId).add(row.getBytes("value"));
            }
            return spans;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraces(traceIds, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraces(Long[] traceIds, int limit) {
        return selectTraces.getQueryString()
                        .replace(':' + KEY_MARKER, Arrays.toString(traceIds))
                        .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public long getSpanTtlSeconds(long traceId) {
        try {
            ByteBuffer key = ByteBuffer.allocate(8).putLong(traceId);
            key.rewind();
            BoundStatement bound = selectTraceTtl.bind().setBytes(KEY_MARKER, key);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceTtl(traceId));
            }
            int ttl = Integer.MAX_VALUE;
            for (Row row : session.execute(bound).all()) {
                ttl = Math.min(ttl, row.getInt(0));
            }
            return ttl;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceTtl(traceId), ex);
            throw ex;
        }
    }

    private String debugSelectTraceTtl(long traceId) {
        return selectTraceTtl.getQueryString().replace(':' + KEY_MARKER, String.valueOf(traceId));
    }

    public List<String> getTopAnnotations(String key) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty());
        try {
            BoundStatement bound = selectTopAnnotations.bind().setBytes(KEY_MARKER, ByteBuffer.wrap(key.getBytes()));
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTopAnnotations(key));
            }
            List<String> annotations = new ArrayList<>();
            for (Row row : session.execute(bound).all()) {
                annotations.add(new String(Bytes.getArray(row.getBytes("value"))));
            }
            return annotations;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTopAnnotations(key), ex);
            throw ex;
        }
    }

    private String debugSelectTopAnnotations(String key) {
        return selectTopAnnotations.getQueryString().replace(':' + KEY_MARKER, key);
    }

    public void storeTopAnnotations(String key, List<String> annotations) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty());
        Preconditions.checkNotNull(annotations);
        Preconditions.checkArgument(!annotations.isEmpty());
        try {
            BoundStatement bound = deleteTopAnnotations.bind().setBytes(KEY_MARKER, ByteBuffer.wrap(key.getBytes()));
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugDeleteTopAnnotations(key));
            }
            session.execute(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugDeleteTopAnnotations(key), ex);
            throw ex;
        }
        StringBuilder debugs = new StringBuilder();
        try {
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
            for (String annotation : annotations) {

                BoundStatement bound = insertTopAnnotations.bind()
                        .setBytes(KEY_MARKER, ByteBuffer.wrap(key.getBytes()))
                        .setLong(COLUMN_MARKER, annotations.indexOf(annotation))
                        .setBytes(VALUE_MARKER, ByteBuffer.wrap(annotation.getBytes()));

                String debug = debugInsertTopAnnotations(key, annotation, annotations.indexOf(annotation));
                debugs.append(debug).append(';');
                LOG.debug(debug);
                batch.add(bound);
            }
            session.executeAsync(batch);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugs, ex);
            throw ex;
        }
    }

    private String debugDeleteTopAnnotations(String key) {
        return deleteTopAnnotations.getQueryString().replace(':' + KEY_MARKER, key);
    }

    private String debugInsertTopAnnotations(String key, String annotation, int idx) {
        return insertTopAnnotations.getQueryString()
                            .replace(':' + KEY_MARKER, key)
                            .replace(':' + COLUMN_MARKER, String.valueOf(idx))
                            .replace(':' + VALUE_MARKER, annotation);
    }

    public void storeDependencies(long startFlooredToDay, ByteBuffer dependencies) {
        try {
            ByteBuffer key = ByteBuffer.allocate(8).putLong(startFlooredToDay);
            key.rewind();

            BoundStatement bound = insertDependencies.bind()
                    .setBytes(KEY_MARKER, key)
                    .setLong(COLUMN_MARKER, 0)
                    .setBytes(VALUE_MARKER, dependencies);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertDependencies(startFlooredToDay, dependencies));
            }
            session.execute(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertDependencies(startFlooredToDay, dependencies), ex);
            throw ex;
        }
    }

    private String debugInsertDependencies(long startFlooredToDay, ByteBuffer dependencies) {
        return insertDependencies.getQueryString()
                        .replace(':' + KEY_MARKER, String.valueOf(startFlooredToDay))
                        .replace(':' + COLUMN_MARKER, String.valueOf(0))
                        .replace(':' + VALUE_MARKER, Bytes.toHexString(dependencies));
    }

    public List<ByteBuffer> getDependencies(long startDate, long endDate) {
        List<Date> days = getDays(endDate, endDate);
        try {
            List<ByteBuffer> keys = new ArrayList<>();
            for (Date day : days) {
                ByteBuffer key = ByteBuffer.allocate(8).putLong(day.getTime());
                key.rewind();
                keys.add(key);
            }
            BoundStatement bound = selectDependencies.bind().setList(KEY_MARKER, keys);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectDependencies(days));
            }
            List<ByteBuffer> dependencies = new ArrayList<>();
            for (Row row : session.execute(bound).all()) {
                dependencies.add(row.getBytes("value"));
            }
            return dependencies;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectDependencies(days), ex);
            throw ex;
        }
    }

    private String debugSelectDependencies(List<Date> days) {
        return selectDependencies.getQueryString().replace(':' + KEY_MARKER, Arrays.toString(days.toArray()));
    }

    public Set<String> getServiceNames() {
        try {
            Set<String> serviceNames = new HashSet<>();
            BoundStatement bound = selectServiceNames.bind();
            if (LOG.isDebugEnabled()) {
                LOG.debug(selectServiceNames.getQueryString());
            }
            for (Row row : session.execute(bound).all()) {
                serviceNames.add(new String(Bytes.getArray(row.getBytes("column1"))));
            }
            return serviceNames;
        } catch (RuntimeException ex) {
            LOG.error("failed " + selectServiceNames.getQueryString(), ex);
            throw ex;
        }
    }

    public void storeServiceName(String serviceName, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {
            BoundStatement bound = insertServiceName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap("servicenames".getBytes()))
                    .setBytes(COLUMN_MARKER, ByteBuffer.wrap(serviceName.getBytes()))
                    .setBytes(VALUE_MARKER, ByteBuffer.wrap("".getBytes()))
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertServiceName(serviceName, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertServiceName(serviceName, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertServiceName(String serviceName, int ttl) {
        return insertServiceName.getQueryString()
                .replace(':' + KEY_MARKER, "servicenames")
                .replace(':' + COLUMN_MARKER, serviceName)
                .replace(':' + VALUE_MARKER, "")
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Set<String> getSpanNames(String serviceName) {
        Preconditions.checkNotNull(serviceName);
        try {
            Set<String> spanNames = new HashSet<>();
            if (!serviceName.isEmpty()) {
                BoundStatement bound = selectSpanNames.bind().setBytes(KEY_MARKER, ByteBuffer.wrap(serviceName.getBytes()));
                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectSpanNames(serviceName));
                }
                for (Row row : session.execute(bound).all()) {
                    spanNames.add(new String(Bytes.getArray(row.getBytes("column1"))));
                }
            }
            return spanNames;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectSpanNames(serviceName), ex);
            throw ex;
        }
    }

    private String debugSelectSpanNames(String serviceName) {
        return selectSpanNames.getQueryString().replace(':' + KEY_MARKER, serviceName);
    }

    public void storeSpanName(String serviceName, String spanName, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            BoundStatement bound = insertSpanName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap(serviceName.getBytes()))
                    .setBytes(COLUMN_MARKER, ByteBuffer.wrap(spanName.getBytes()))
                    .setBytes(VALUE_MARKER, ByteBuffer.wrap("".getBytes()))
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertSpanName(serviceName, spanName, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertSpanName(serviceName, spanName, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertSpanName(String serviceName, String spanName, int ttl) {
        return insertSpanName.getQueryString()
                .replace(':' + KEY_MARKER, serviceName)
                .replace(':' + COLUMN_MARKER, spanName)
                .replace(':' + VALUE_MARKER, "")
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByServiceName(String serviceName, long to, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {
            BoundStatement bound = selectTraceIdsByServiceName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap(serviceName.getBytes()))
                    .setLong(COLUMN_MARKER, to)
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByServiceName(serviceName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getBytes("value").getLong(), row.getLong("column1"));
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByServiceName(serviceName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByServiceName(String serviceName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(':' + KEY_MARKER, serviceName)
                .replace(':' + COLUMN_MARKER, String.valueOf(to))
                .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {
            ByteBuffer traceIdBytes = ByteBuffer.allocate(8).putLong(traceId);
            traceIdBytes.rewind();

            BoundStatement bound = insertTraceIdByServiceName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap(serviceName.getBytes()))
                    .setLong(COLUMN_MARKER, timestamp)
                    .setBytes(VALUE_MARKER, traceIdBytes)
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdByServiceName(serviceName, timestamp, traceId, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdByServiceName(serviceName, timestamp, traceId, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {
        return insertTraceIdByServiceName.getQueryString()
                        .replace(':' + KEY_MARKER, serviceName)
                        .replace(':' + COLUMN_MARKER, String.valueOf(timestamp))
                        .replace(':' + VALUE_MARKER, String.valueOf(traceId))
                        .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsBySpanName(String serviceName, String spanName, long to, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        String serviceSpanName = serviceName + "." + spanName;
        try {
            BoundStatement bound = selectTraceIdsBySpanName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap(serviceSpanName.getBytes()))
                    .setLong(COLUMN_MARKER, to)
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsBySpanName(serviceSpanName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getBytes("value").getLong(), row.getLong("column1"));
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsBySpanName(serviceSpanName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsBySpanName(String serviceSpanName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(':' + KEY_MARKER, serviceSpanName)
                .replace(':' + COLUMN_MARKER, String.valueOf(to))
                .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdBySpanName(String serviceName, String spanName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            String serviceSpanName = serviceName + "." + spanName;
            ByteBuffer traceIdBytes = ByteBuffer.allocate(8).putLong(traceId);
            traceIdBytes.rewind();

            BoundStatement bound = insertTraceIdBySpanName.bind()
                    .setBytes(KEY_MARKER, ByteBuffer.wrap(serviceSpanName.getBytes()))
                    .setLong(COLUMN_MARKER, timestamp)
                    .setBytes(VALUE_MARKER, traceIdBytes)
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdBySpanName(serviceSpanName, timestamp, traceId, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdBySpanName(serviceName, timestamp, traceId, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertTraceIdBySpanName(String serviceSpanName, long timestamp, long traceId, int ttl) {
        return insertTraceIdBySpanName.getQueryString()
                .replace(':' + KEY_MARKER, serviceSpanName)
                .replace(':' + COLUMN_MARKER, String.valueOf(timestamp))
                .replace(':' + VALUE_MARKER, String.valueOf(traceId))
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByAnnotation(ByteBuffer annotationKey, long from, int limit) {
        try {
            BoundStatement bound = selectTraceIdsByAnnotations.bind()
                    .setBytes(KEY_MARKER, annotationKey)
                    .setLong(COLUMN_MARKER, from)
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByAnnotations(annotationKey, from, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getBytes("value").getLong(), row.getLong("column1"));
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByAnnotations(annotationKey, from, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByAnnotations(ByteBuffer annotationKey, long from, int limit) {
        return selectTraceIdsByAnnotations.getQueryString()
                        .replace(':' + KEY_MARKER, new String(Bytes.getArray(annotationKey)))
                        .replace(':' + COLUMN_MARKER, String.valueOf(from))
                        .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        try {
            ByteBuffer traceIdBytes = ByteBuffer.allocate(8).putLong(traceId);
            traceIdBytes.rewind();

            BoundStatement bound = insertTraceIdByAnnotation.bind()
                    .setBytes(KEY_MARKER, annotationKey)
                    .setLong(COLUMN_MARKER, timestamp)
                    .setBytes(VALUE_MARKER, traceIdBytes)
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceIdByAnnotation(annotationKey, timestamp, traceId, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceIdByAnnotation(annotationKey, timestamp, traceId, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        return insertTraceIdByAnnotation.getQueryString()
                .replace(':' + KEY_MARKER, new String(Bytes.getArray(annotationKey)))
                .replace(':' + COLUMN_MARKER, String.valueOf(timestamp))
                .replace(':' + VALUE_MARKER, String.valueOf(traceId))
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceDuration(boolean head, long[] traceIds) {
        Preconditions.checkNotNull(traceIds);
        Preconditions.checkArgument(0 < traceIds.length);
        List<ResultSetFuture> futures = new ArrayList<>();
        for (Long traceId : traceIds) {
            try {
                assert null != traceId;
                ByteBuffer key = ByteBuffer.allocate(8).putLong(traceId);
                key.rewind();

                BoundStatement bound = (head ? selectTraceDurationHead : selectTraceDurationTail)
                        .bind()
                        .setBytes(KEY_MARKER, key);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectTraceDuration(head, traceId));
                }
                futures.add(session.executeAsync(bound));
            } catch (RuntimeException ex) {
                LOG.error("failed " + debugSelectTraceDuration(head, traceId), ex);
                throw ex;
            }
        }

        Map<Long,Long> results = new HashMap<>();
        for (ResultSetFuture future : futures) {
            Row row = future.getUninterruptibly().one();
            results.put(row.getBytes("key").getLong(), row.getLong("column1"));
        }
        return results;
    }

    private String debugSelectTraceDuration(boolean head, long traceId) {
        return (head ? selectTraceDurationHead : selectTraceDurationTail).getQueryString()
                .replace(':' + KEY_MARKER, String.valueOf(traceId));
    }

    public void storeTraceDuration(long traceId, long timestamp, int ttl) {
        try {
            ByteBuffer key = ByteBuffer.allocate(8).putLong(traceId);
            key.rewind();

            BoundStatement bound = insertTraceDurations.bind()
                    .setBytes(KEY_MARKER, key)
                    .setLong(COLUMN_MARKER, timestamp)
                    .setBytes(VALUE_MARKER, ByteBuffer.wrap("".getBytes()))
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertTraceDurations(traceId, timestamp, ttl));
            }
            session.execute(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertTraceDurations(traceId, timestamp, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertTraceDurations(long traceId, long timestamp, int ttl) {
        return insertTraceDurations.getQueryString()
                .replace(':' + KEY_MARKER, String.valueOf(traceId))
                .replace(':' + COLUMN_MARKER, String.valueOf(timestamp))
                .replace(':' + VALUE_MARKER, "")
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    private static List<Date> getDays(long from, long to) {
        List<Date> days = new ArrayList<>();
        Calendar day = getDay(from);
        do {
            days.add(day.getTime());
            day = getDay(day.getTimeInMillis() + TimeUnit.DAYS.toMillis(1));
        } while (day.getTimeInMillis() <= to);
        return days;
    }

    private static Calendar getDay(long from) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(from);
        day.set(Calendar.MILLISECOND, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.HOUR, 0);
        return day;
    }

    @Override
    public void close() {
        session.close();
    }

    private static class Schema {

        private static final String SCHEMA = "/cassandra-schema-cql3.txt";

        static void ensureExists(String keyspace, Cluster cluster) {
            try (Session session = cluster.connect()) {
                try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(SCHEMA))) {
                    for (String cmd : String.format(CharStreams.toString(reader)).split(";")) {
                        cmd = cmd.trim().replace(" zipkin", " " + keyspace);
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

}
