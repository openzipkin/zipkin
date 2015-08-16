
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
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

    public static final String KEYSPACE = "zipkin";

    private static final String TRACE_ID = "trace_id";
    private static final String SPAN_NAME = "span_name";
    private static final String SERVICE_NAME = "service_name";
    private static final String TIMESTAMP = "ts";
    private static final String DAY = "day";
    private static final String DEPENDENCIES = "dependencies";
    private static final String ANNOTATION = "annotation";
    private static final String KEY = "key";
    private static final String IDX = "idx";
    private static final String SERVICE_SPAN_NAME = "service_span_name";
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
    private final Map<String,String> metadata;


    public Repository(String keyspace, Cluster cluster) {
        metadata = Schema.ensureExists(keyspace, cluster);
        session = cluster.connect(keyspace);

        insertSpan = session.prepare(
                QueryBuilder
                    .insertInto("traces")
                    .value(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))
                    .value(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP))
                    .value(SPAN_NAME, QueryBuilder.bindMarker(SPAN_NAME))
                    .value("span", QueryBuilder.bindMarker(VALUE_MARKER))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraces = session.prepare(
                QueryBuilder.select(TRACE_ID, "span")
                    .from("traces")
                    .where(QueryBuilder.in(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER)));

        selectTraceTtl = session.prepare(
                QueryBuilder.select()
                    .ttl("span")
                    .from("traces")
                    .where(QueryBuilder.eq(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))));

        selectTopAnnotations = session.prepare(
                QueryBuilder.select(ANNOTATION)
                    .from("top_annotations")
                    .where(QueryBuilder.in(KEY, QueryBuilder.bindMarker(KEY))));

        deleteTopAnnotations = session.prepare(
                QueryBuilder
                    .delete()
                    .from("top_annotations")
                    .where(QueryBuilder.eq(KEY, QueryBuilder.bindMarker(KEY))));

        insertTopAnnotations = session.prepare(
                QueryBuilder
                    .insertInto("top_annotations")
                    .value(KEY, QueryBuilder.bindMarker(KEY))
                    .value(IDX, QueryBuilder.bindMarker(IDX))
                    .value(ANNOTATION, QueryBuilder.bindMarker(ANNOTATION)));

        selectDependencies = session.prepare(
                QueryBuilder.select(DEPENDENCIES)
                    .from("dependencies")
                    .where(QueryBuilder.in(DAY, QueryBuilder.bindMarker(DAY))));

        insertDependencies = session.prepare(
                QueryBuilder
                    .insertInto("dependencies")
                    .value(DAY, QueryBuilder.bindMarker(DAY))
                    .value(DEPENDENCIES, QueryBuilder.bindMarker(DEPENDENCIES)));

        selectServiceNames = session.prepare(
                QueryBuilder.select(SERVICE_NAME)
                    .from("service_names"));

        insertServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_names")
                    .value(SERVICE_NAME, QueryBuilder.bindMarker(SERVICE_NAME))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectSpanNames = session.prepare(
                QueryBuilder.select(SPAN_NAME)
                    .from("span_names")
                    .where(QueryBuilder.eq(SERVICE_NAME, QueryBuilder.bindMarker(SERVICE_NAME))));

        insertSpanName = session.prepare(
                QueryBuilder
                    .insertInto("span_names")
                    .value(SERVICE_NAME, QueryBuilder.bindMarker(SERVICE_NAME))
                    .value(SPAN_NAME, QueryBuilder.bindMarker(SPAN_NAME))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsByServiceName = session.prepare(
                QueryBuilder.select(TIMESTAMP, TRACE_ID)
                    .from("service_name_index")
                    .where(QueryBuilder.eq(SERVICE_NAME, QueryBuilder.bindMarker(SERVICE_NAME)))
                    .and(QueryBuilder.lte(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                    .orderBy(QueryBuilder.desc(TIMESTAMP)));

        insertTraceIdByServiceName = session.prepare(
                QueryBuilder
                    .insertInto("service_name_index")
                    .value(SERVICE_NAME, QueryBuilder.bindMarker(SERVICE_NAME))
                    .value(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP))
                    .value(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsBySpanName = session.prepare(
                QueryBuilder.select(TIMESTAMP, TRACE_ID)
                    .from("service_span_name_index")
                    .where(QueryBuilder.eq(SERVICE_SPAN_NAME, QueryBuilder.bindMarker(SERVICE_SPAN_NAME)))
                    .and(QueryBuilder.lte(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                    .orderBy(QueryBuilder.desc(TIMESTAMP)));

        insertTraceIdBySpanName = session.prepare(
                QueryBuilder
                    .insertInto("service_span_name_index")
                    .value(SERVICE_SPAN_NAME, QueryBuilder.bindMarker(SERVICE_SPAN_NAME))
                    .value(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP))
                    .value(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceIdsByAnnotations = session.prepare(
                QueryBuilder.select(TIMESTAMP, TRACE_ID)
                    .from("annotations_index")
                    .where(QueryBuilder.eq(ANNOTATION, QueryBuilder.bindMarker(ANNOTATION)))
                    .and(QueryBuilder.lte(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP)))
                    .limit(QueryBuilder.bindMarker(LIMIT_MARKER))
                    .orderBy(QueryBuilder.desc(TIMESTAMP)));

        insertTraceIdByAnnotation = session.prepare(
                QueryBuilder
                    .insertInto("annotations_index")
                    .value(ANNOTATION, QueryBuilder.bindMarker(ANNOTATION))
                    .value(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP))
                    .value(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));

        selectTraceDurationHead = session.prepare(
                QueryBuilder.select(TRACE_ID, TIMESTAMP)
                    .from("duration_index")
                    .where(QueryBuilder.eq(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID)))
                    .limit(1)
                    .orderBy(QueryBuilder.asc(TIMESTAMP)));

        selectTraceDurationTail = session.prepare(
                QueryBuilder.select(TRACE_ID, TIMESTAMP)
                    .from("duration_index")
                    .where(QueryBuilder.eq(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID)))
                    .limit(1)
                    .orderBy(QueryBuilder.desc(TIMESTAMP)));

        insertTraceDurations = session.prepare(
                QueryBuilder
                    .insertInto("duration_index")
                    .value(TRACE_ID, QueryBuilder.bindMarker(TRACE_ID))
                    .value(TIMESTAMP, QueryBuilder.bindMarker(TIMESTAMP))
                    .using(QueryBuilder.ttl(QueryBuilder.bindMarker(TTL_MARKER))));
    }

    /**
     * Store the span in the underlying storage for later retrieval.
     */
    public void storeSpan(long traceId, long timestamp, String spanName, ByteBuffer span, int ttl) {
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        if (0 == timestamp && metadata.get("traces.compaction.class").contains("DateTieredCompactionStrategy")) {
            LOG.warn("span with no first or last timestamp. "
                    + "if this happens a lot consider switching back to SizeTieredCompactionStrategy for "
                    + KEYSPACE + ".traces");
        }
        try {

            BoundStatement bound = insertSpan.bind()
                    .setLong(TRACE_ID, traceId)
                    .setDate(TIMESTAMP, new Date(timestamp))
                    .setString(SPAN_NAME, spanName)
                    .setBytes(VALUE_MARKER, span)
                    .setInt(TTL_MARKER, ttl);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertSpan(traceId, timestamp, spanName, span, ttl));
            }
            session.executeAsync(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertSpan(traceId, timestamp, spanName, span, ttl), ex);
            throw ex;
        }
    }

    private String debugInsertSpan(long traceId, long timestamp, String spanName, ByteBuffer span, int ttl) {
        return insertSpan.getQueryString()
                .replace(':' + TRACE_ID, String.valueOf(traceId))
                .replace(':' + TIMESTAMP, String.valueOf(timestamp))
                .replace(':' + SPAN_NAME, spanName)
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
        try {
            Map<Long,List<ByteBuffer>> spans = new HashMap<>();
            if (0 < traceIds.length) {

                BoundStatement bound = selectTraces.bind()
                        .setList(TRACE_ID, Arrays.asList(traceIds))
                        .setInt(LIMIT_MARKER, limit);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectTraces(traceIds, limit));
                }
                for (Row row : session.execute(bound).all()) {
                    long traceId = row.getLong(TRACE_ID);
                    if (!spans.containsKey(traceId)) {
                        spans.put(traceId, new ArrayList<ByteBuffer>());
                    }
                    spans.get(traceId).add(row.getBytes("span"));
                }
            }
            return spans;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraces(traceIds, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraces(Long[] traceIds, int limit) {
        return selectTraces.getQueryString()
                        .replace(':' + TRACE_ID, Arrays.toString(traceIds))
                        .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public long getSpanTtlSeconds(long traceId) {
        try {
            BoundStatement bound = selectTraceTtl.bind().setLong(TRACE_ID, traceId);
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
        return selectTraceTtl.getQueryString().replace(':' + TRACE_ID, String.valueOf(traceId));
    }

    public List<String> getTopAnnotations(String key) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty());
        try {
            BoundStatement bound = selectTopAnnotations.bind().setString(KEY, key);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTopAnnotations(key));
            }
            List<String> annotations = new ArrayList<>();
            for (Row row : session.execute(bound).all()) {
                annotations.add(row.getString(ANNOTATION));
            }
            return annotations;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTopAnnotations(key), ex);
            throw ex;
        }
    }

    private String debugSelectTopAnnotations(String key) {
        return selectTopAnnotations.getQueryString().replace(':' + KEY, key);
    }

    public void storeTopAnnotations(String key, List<String> annotations) {
        Preconditions.checkNotNull(key);
        Preconditions.checkArgument(!key.isEmpty());
        Preconditions.checkNotNull(annotations);
        Preconditions.checkArgument(!annotations.isEmpty());
        try {
            BoundStatement bound = deleteTopAnnotations.bind().setString(KEY, key);
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
                        .setString(KEY, key)
                        .setInt(IDX, annotations.indexOf(annotation))
                        .setString(ANNOTATION, annotation);

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
        return deleteTopAnnotations.getQueryString().replace(':' + KEY, key);
    }

    private String debugInsertTopAnnotations(String key, String annotation, int idx) {
        return insertTopAnnotations.getQueryString()
                            .replace(':' + KEY, key)
                            .replace(':' + IDX, String.valueOf(idx))
                            .replace(':' + ANNOTATION, annotation);
    }

    public void storeDependencies(long startFlooredToDay, ByteBuffer dependencies) {
        try {

            BoundStatement bound = insertDependencies.bind()
                    .setDate(DAY, new Date(startFlooredToDay))
                    .setBytes(DEPENDENCIES, dependencies);

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
                        .replace(':' + DAY, new Date(startFlooredToDay).toString())
                        .replace(':' + DEPENDENCIES, Bytes.toHexString(dependencies));
    }

    public List<ByteBuffer> getDependencies(long startDate, long endDate) {
        List<Date> days = getDays(endDate, endDate);
        try {
            BoundStatement bound = selectDependencies.bind().setList(DAY, days);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectDependencies(days));
            }
            List<ByteBuffer> dependencies = new ArrayList<>();
            for (Row row : session.execute(bound).all()) {
                dependencies.add(row.getBytes(DEPENDENCIES));
            }
            return dependencies;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectDependencies(days), ex);
            throw ex;
        }
    }

    private String debugSelectDependencies(List<Date> days) {
        return selectDependencies.getQueryString().replace(':' + DAY, Arrays.toString(days.toArray()));
    }

    public Set<String> getServiceNames() {
        try {
            Set<String> serviceNames = new HashSet<>();
            BoundStatement bound = selectServiceNames.bind();
            if (LOG.isDebugEnabled()) {
                LOG.debug(selectServiceNames.getQueryString());
            }
            for (Row row : session.execute(bound).all()) {
                serviceNames.add(row.getString(SERVICE_NAME));
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
                    .setString(SERVICE_NAME, serviceName)
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
                .replace(':' + SERVICE_NAME, serviceName)
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Set<String> getSpanNames(String serviceName) {
        Preconditions.checkNotNull(serviceName);
        try {
            Set<String> spanNames = new HashSet<>();
            if (!serviceName.isEmpty()) {
                BoundStatement bound = selectSpanNames.bind().setString(SERVICE_NAME, serviceName);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectSpanNames(serviceName));
                }
                for (Row row : session.execute(bound).all()) {
                    spanNames.add(row.getString(SPAN_NAME));
                }
            }
            return spanNames;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectSpanNames(serviceName), ex);
            throw ex;
        }
    }

    private String debugSelectSpanNames(String serviceName) {
        return selectSpanNames.getQueryString().replace(':' + SERVICE_NAME, serviceName);
    }

    public void storeSpanName(String serviceName, String spanName, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            BoundStatement bound = insertSpanName.bind()
                    .setString(SERVICE_NAME, serviceName)
                    .setString(SPAN_NAME, spanName)
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
                .replace(':' + SERVICE_NAME, serviceName)
                .replace(':' + SPAN_NAME, spanName)
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByServiceName(String serviceName, long to, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {
            BoundStatement bound = selectTraceIdsByServiceName.bind()
                    .setString(SERVICE_NAME, serviceName)
                    .setDate(TIMESTAMP, new Date(to))
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByServiceName(serviceName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong(TRACE_ID), row.getDate(TIMESTAMP).getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByServiceName(serviceName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByServiceName(String serviceName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(':' + SERVICE_NAME, serviceName)
                .replace(':' + TIMESTAMP, new Date(to).toString())
                .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {

            BoundStatement bound = insertTraceIdByServiceName.bind()
                    .setString(SERVICE_NAME, serviceName)
                    .setDate(TIMESTAMP, new Date(timestamp))
                    .setLong(TRACE_ID, traceId)
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
                        .replace(':' + SERVICE_NAME, serviceName)
                        .replace(':' + TIMESTAMP, new Date(timestamp).toString())
                        .replace(':' + TRACE_ID, new Date(traceId).toString())
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
                    .setString(SERVICE_SPAN_NAME, serviceSpanName)
                    .setDate(TIMESTAMP, new Date(to))
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsBySpanName(serviceSpanName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong(TRACE_ID), row.getDate(TIMESTAMP).getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsBySpanName(serviceSpanName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsBySpanName(String serviceSpanName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(':' + SERVICE_SPAN_NAME, serviceSpanName)
                .replace(':' + TIMESTAMP, new Date(to).toString())
                .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdBySpanName(String serviceName, String spanName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            String serviceSpanName = serviceName + "." + spanName;

            BoundStatement bound = insertTraceIdBySpanName.bind()
                    .setString(SERVICE_SPAN_NAME, serviceSpanName)
                    .setDate(TIMESTAMP, new Date(timestamp))
                    .setLong(TRACE_ID, traceId)
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
                .replace(':' + SERVICE_SPAN_NAME, serviceSpanName)
                .replace(':' + TIMESTAMP, String.valueOf(timestamp))
                .replace(':' + TRACE_ID, String.valueOf(traceId))
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByAnnotation(ByteBuffer annotationKey, long from, int limit) {
        try {
            BoundStatement bound = selectTraceIdsByAnnotations.bind()
                    .setBytes(ANNOTATION, annotationKey)
                    .setDate(TIMESTAMP, new Date(from))
                    .setInt(LIMIT_MARKER, limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByAnnotations(annotationKey, from, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong(TRACE_ID), row.getDate(TIMESTAMP).getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByAnnotations(annotationKey, from, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByAnnotations(ByteBuffer annotationKey, long from, int limit) {
        return selectTraceIdsByAnnotations.getQueryString()
                        .replace(':' + ANNOTATION, new String(Bytes.getArray(annotationKey)))
                        .replace(':' + TIMESTAMP, new Date(from).toString())
                        .replace(':' + LIMIT_MARKER, String.valueOf(limit));
    }

    public void storeTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        try {
            BoundStatement bound = insertTraceIdByAnnotation.bind()
                    .setBytes(ANNOTATION, annotationKey)
                    .setDate(TIMESTAMP, new Date(timestamp))
                    .setLong(TRACE_ID, traceId)
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
                .replace(':' + ANNOTATION, new String(Bytes.getArray(annotationKey)))
                .replace(':' + TIMESTAMP, new Date(timestamp).toString())
                .replace(':' + TRACE_ID, String.valueOf(traceId))
                .replace(':' + TTL_MARKER, String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceDuration(boolean head, long[] traceIds) {

        // @todo upgrade to aggregate functions in Cassandra-2.2
        //  with min(..) and max(..) functions in CQL the logic here and above in CassandraSpanStore can be simplified
        //  ref CASSANDRA-4914

        Preconditions.checkNotNull(traceIds);
        List<ResultSetFuture> futures = new ArrayList<>();
        for (Long traceId : traceIds) {
            try {

                BoundStatement bound = (head ? selectTraceDurationHead : selectTraceDurationTail)
                        .bind()
                        .setLong(TRACE_ID, traceId);

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
            results.put(row.getLong(TRACE_ID), row.getDate(TIMESTAMP).getTime());
        }
        return results;
    }

    private String debugSelectTraceDuration(boolean head, long traceId) {
        return (head ? selectTraceDurationHead : selectTraceDurationTail).getQueryString()
                .replace(':' + TRACE_ID, String.valueOf(traceId));
    }

    public void storeTraceDuration(long traceId, long timestamp, int ttl) {
        try {

            BoundStatement bound = insertTraceDurations.bind()
                    .setLong(TRACE_ID, traceId)
                    .setDate(TIMESTAMP, new Date(timestamp))
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
                .replace(':' + TRACE_ID, String.valueOf(traceId))
                .replace(':' + TIMESTAMP, new Date(timestamp).toString())
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

        static Map<String,String> ensureExists(String keyspace, Cluster cluster) {
            Map<String,String> metadata = new HashMap<>();
            try (Session session = cluster.connect()) {
                try (Reader reader = new InputStreamReader(Schema.class.getResourceAsStream(SCHEMA))) {
                    for (String cmd : String.format(CharStreams.toString(reader)).split(";")) {
                        cmd = cmd.trim().replace(" " + KEYSPACE, " " + keyspace);
                        if (!cmd.isEmpty()) {
                            session.execute(cmd);
                        }
                    }
                }
                KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);
                Map<String,String> replicatn = keyspaceMetadata.getReplication();
                if("SimpleStrategy".equals(replicatn.get("class")) && "1".equals(replicatn.get("replication_factor"))) {
                    LOG.warn("running with RF=1, this is not suitable for production. Optimal is 3+");
                }
                Map<String,String> tracesCompaction = keyspaceMetadata.getTable("traces").getOptions().getCompaction();
                metadata.put("traces.compaction.class", tracesCompaction.get("class"));
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            return metadata;
        }

        private Schema() {}
    }

}
