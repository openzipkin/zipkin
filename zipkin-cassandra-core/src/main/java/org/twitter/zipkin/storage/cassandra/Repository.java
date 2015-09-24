
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PreparedStatement;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private final Map<String,String> metadata;

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

    public Repository(String keyspace, Cluster cluster) {
        metadata = Schema.ensureExists(keyspace, cluster);
        session = cluster.connect(keyspace);

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
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("ts")))
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
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("ts")))
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
                    .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("ts")))
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
                    .setLong("trace_id", traceId)
                    .setDate("ts", new Date(timestamp))
                    .setString("span_name", spanName)
                    .setBytes("span", span)
                    .setInt("ttl_", ttl);

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
                        .setList("trace_id", Arrays.asList(traceIds))
                        .setInt("limit_", limit);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectTraces(traceIds, limit));
                }
                for (Row row : session.execute(bound).all()) {
                    long traceId = row.getLong("trace_id");
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
                        .replace(":trace_id", Arrays.toString(traceIds))
                        .replace(":limit_", String.valueOf(limit));
    }

    public void storeDependencies(long startMillis, ByteBuffer dependencies) {
        Date startFlooredToDay = getDay(startMillis).getTime();
        try {
            BoundStatement bound = insertDependencies.bind()
                    .setDate("day", startFlooredToDay)
                    .setBytes("dependencies", dependencies);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugInsertDependencies(startFlooredToDay, dependencies));
            }
            session.execute(bound);
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugInsertDependencies(startFlooredToDay, dependencies), ex);
            throw ex;
        }
    }

    private String debugInsertDependencies(Date startFlooredToDay, ByteBuffer dependencies) {
        return insertDependencies.getQueryString()
                        .replace(":day", startFlooredToDay.toString())
                        .replace(":dependencies", Bytes.toHexString(dependencies));
    }

    public List<ByteBuffer> getDependencies(long startMillis, long endMillis) {
        List<Date> days = getDays(startMillis, endMillis);
        try {
            BoundStatement bound = selectDependencies.bind().setList("days", days);
            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectDependencies(days));
            }
            List<ByteBuffer> dependencies = new ArrayList<>();
            for (Row row : session.execute(bound).all()) {
                dependencies.add(row.getBytes("dependencies"));
            }
            return dependencies;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectDependencies(days), ex);
            throw ex;
        }
    }

    private String debugSelectDependencies(List<Date> days) {
        return selectDependencies.getQueryString().replace(":days", Arrays.toString(days.toArray()));
    }

    public Set<String> getServiceNames() {
        try {
            Set<String> serviceNames = new HashSet<>();
            BoundStatement bound = selectServiceNames.bind();
            if (LOG.isDebugEnabled()) {
                LOG.debug(selectServiceNames.getQueryString());
            }
            for (Row row : session.execute(bound).all()) {
                serviceNames.add(row.getString("service_name"));
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
        if (writtenNames.get().add(serviceName)) {
            try {
                BoundStatement bound = insertServiceName.bind()
                        .setString("service_name", serviceName)
                        .setInt("ttl_", ttl);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugInsertServiceName(serviceName, ttl));
                }
                session.executeAsync(bound);
            } catch (RuntimeException ex) {
                LOG.error("failed " + debugInsertServiceName(serviceName, ttl), ex);
                writtenNames.get().remove(serviceName);
                throw ex;
            }
        }
    }

    private String debugInsertServiceName(String serviceName, int ttl) {
        return insertServiceName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":ttl_", String.valueOf(ttl));
    }

    public Set<String> getSpanNames(String serviceName) {
        Preconditions.checkNotNull(serviceName);
        try {
            Set<String> spanNames = new HashSet<>();
            if (!serviceName.isEmpty()) {

                BoundStatement bound = selectSpanNames.bind()
                        .setString("service_name", serviceName)
                        .setInt("bucket", 0);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(debugSelectSpanNames(serviceName));
                }
                for (Row row : session.execute(bound).all()) {
                    spanNames.add(row.getString("span_name"));
                }
            }
            return spanNames;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectSpanNames(serviceName), ex);
            throw ex;
        }
    }

    private String debugSelectSpanNames(String serviceName) {
        return selectSpanNames.getQueryString().replace(':' + "service_name", serviceName);
    }

    public void storeSpanName(String serviceName, String spanName, int ttl) {
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
                session.executeAsync(bound);
            } catch (RuntimeException ex) {
                LOG.error("failed " + debugInsertSpanName(serviceName, spanName, ttl), ex);
                writtenNames.get().remove(serviceName + "––" + spanName);
                throw ex;
            }
        }
    }

    private String debugInsertSpanName(String serviceName, String spanName, int ttl) {
        return insertSpanName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":span_name", spanName)
                .replace(":ttl_", String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByServiceName(String serviceName, long to, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {
            BoundStatement bound = selectTraceIdsByServiceName.bind()
                    .setString("service_name", serviceName)
                    .setList("bucket", ALL_BUCKETS)
                    .setDate("ts", new Date(to))
                    .setInt("limit_", limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByServiceName(serviceName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong("trace_id"), row.getDate("ts").getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByServiceName(serviceName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByServiceName(String serviceName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(":service_name", serviceName)
                .replace(":ts", new Date(to).toString())
                .replace(":limit_", String.valueOf(limit));
    }

    public void storeTraceIdByServiceName(String serviceName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        try {

            BoundStatement bound = insertTraceIdByServiceName.bind()
                    .setString("service_name", serviceName)
                    .setInt("bucket", RAND.nextInt(BUCKETS))
                    .setDate("ts", new Date(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

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
                        .replace(":service_name", serviceName)
                        .replace(":ts", new Date(timestamp).toString())
                        .replace(":trace_id", new Date(traceId).toString())
                        .replace(":ttl_", String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsBySpanName(String serviceName, String spanName, long to, int limit) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        String serviceSpanName = serviceName + "." + spanName;
        try {
            BoundStatement bound = selectTraceIdsBySpanName.bind()
                    .setString("service_span_name", serviceSpanName)
                    .setDate("ts", new Date(to))
                    .setInt("limit_", limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsBySpanName(serviceSpanName, to, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong("trace_id"), row.getDate("ts").getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsBySpanName(serviceSpanName, to, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsBySpanName(String serviceSpanName, long to, int limit) {
        return selectTraceIdsByServiceName.getQueryString()
                .replace(":service_span_name", serviceSpanName)
                .replace(":ts", new Date(to).toString())
                .replace(":limit_", String.valueOf(limit));
    }

    public void storeTraceIdBySpanName(String serviceName, String spanName, long timestamp, long traceId, int ttl) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkArgument(!serviceName.isEmpty());
        Preconditions.checkNotNull(spanName);
        Preconditions.checkArgument(!spanName.isEmpty());
        try {
            String serviceSpanName = serviceName + "." + spanName;

            BoundStatement bound = insertTraceIdBySpanName.bind()
                    .setString("service_span_name", serviceSpanName)
                    .setDate("ts", new Date(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

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
                .replace(":service_span_name", serviceSpanName)
                .replace(":ts", String.valueOf(timestamp))
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ttl_", String.valueOf(ttl));
    }

    public Map<Long,Long> getTraceIdsByAnnotation(ByteBuffer annotationKey, long from, int limit) {
        try {
            BoundStatement bound = selectTraceIdsByAnnotations.bind()
                    .setBytes("annotation", annotationKey)
                    .setList("bucket", ALL_BUCKETS)
                    .setDate("ts", new Date(from))
                    .setInt("limit_", limit);

            if (LOG.isDebugEnabled()) {
                LOG.debug(debugSelectTraceIdsByAnnotations(annotationKey, from, limit));
            }
            Map<Long,Long> traceIdsToTimestamps = new HashMap<>();
            for (Row row : session.execute(bound).all()) {
                traceIdsToTimestamps.put(row.getLong("trace_id"), row.getDate("ts").getTime());
            }
            return traceIdsToTimestamps;
        } catch (RuntimeException ex) {
            LOG.error("failed " + debugSelectTraceIdsByAnnotations(annotationKey, from, limit), ex);
            throw ex;
        }
    }

    private String debugSelectTraceIdsByAnnotations(ByteBuffer annotationKey, long from, int limit) {
        return selectTraceIdsByAnnotations.getQueryString()
                        .replace(":annotation", new String(Bytes.getArray(annotationKey)))
                        .replace(":ts", new Date(from).toString())
                        .replace(":limit_", String.valueOf(limit));
    }

    public void storeTraceIdByAnnotation(ByteBuffer annotationKey, long timestamp, long traceId, int ttl) {
        try {
            BoundStatement bound = insertTraceIdByAnnotation.bind()
                    .setBytes("annotation", annotationKey)
                    .setInt("bucket", RAND.nextInt(BUCKETS))
                    .setDate("ts", new Date(timestamp))
                    .setLong("trace_id", traceId)
                    .setInt("ttl_", ttl);

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
                .replace(":annotation", new String(Bytes.getArray(annotationKey)))
                .replace(":ts", new Date(timestamp).toString())
                .replace(":trace_id", String.valueOf(traceId))
                .replace(":ttl_", String.valueOf(ttl));
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
