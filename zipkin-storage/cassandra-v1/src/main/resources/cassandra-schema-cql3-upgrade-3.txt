CREATE TABLE IF NOT EXISTS zipkin.remote_service_names (
    service_name text,
    remote_service_name text,
    PRIMARY KEY (service_name, remote_service_name)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND default_time_to_live =  259200;

CREATE TABLE IF NOT EXISTS zipkin.service_remote_service_name_index (
    service_remote_service_name text,      // Span.localServiceName + "." + Span.remoteServiceName
    ts                          timestamp, // start timestamp of the span, truncated to millisecond precision
    trace_id                    bigint,    // trace ID. Included as a clustering column to avoid clashes (however unlikely)
    PRIMARY KEY (service_remote_service_name, ts, trace_id)
)
    WITH CLUSTERING ORDER BY (ts DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.DateTieredCompactionStrategy', 'max_window_size_seconds': '86400'}
    AND default_time_to_live =  259200;
