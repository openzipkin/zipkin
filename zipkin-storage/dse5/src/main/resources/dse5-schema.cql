CREATE KEYSPACE IF NOT EXISTS zipkin3 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};

CREATE TYPE IF NOT EXISTS zipkin3.endpoint (
    service_name text,
    ipv4         inet,
    ipv6         inet,
    port         smallint,
);

CREATE TYPE IF NOT EXISTS zipkin3.annotation (
    ts bigint,
    v  text,
    ep frozen<endpoint>
);

CREATE TYPE IF NOT EXISTS zipkin3.binary_annotation (
    k  text,
    v  blob,
    t  text,
    ep frozen<endpoint>
);

CREATE TABLE IF NOT EXISTS zipkin3.traces (
    trace_id            varint,
    ts_uuid             timeuuid,
    id                  bigint,
    ts                  bigint,
    span_name           text,
    parent_id           bigint,
    duration            bigint,
    annotations         list<frozen<annotation>>,
    binary_annotations  list<frozen<binary_annotation>>,
    all_annotations     text, // can't do SASI on set<text>: comma-joined until CASSANDRA-11182
    PRIMARY KEY (trace_id, ts_uuid, id)
)
    WITH CLUSTERING ORDER BY (ts_uuid DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
    AND default_time_to_live =  604800;


CREATE TABLE zipkin3.trace_by_service_span (
    service_name  text,      // service name
    span_name     text,      // span name, or blank for queries without span name
    bucket        int,       // time bucket, calculated as ts/interval (in microseconds), for some pre-configured interval like 1 day.
    ts            timeuuid,  // start timestamp of the span, truncated to millisecond precision
    trace_id      varint,    // trace ID
    duration      bigint,    // span duration, in microseconds
    PRIMARY KEY ((service_name, span_name, bucket), ts)
)
   WITH CLUSTERING ORDER BY (ts DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
    AND default_time_to_live =  259200;


CREATE MATERIALIZED VIEW IF NOT EXISTS zipkin3.trace_by_service
    AS SELECT service_name, span_name, bucket, ts, trace_id FROM trace_by_service_span
            WHERE service_name is not null AND span_name is not null AND bucket is not null AND ts is not null
            PRIMARY KEY (service_name, bucket, span_name, ts)
        WITH CLUSTERING ORDER BY (bucket DESC, ts DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
        AND default_time_to_live =  259200;

CREATE TABLE IF NOT EXISTS zipkin3.dependencies (
    day          timestamp,
    links        blob,
    PRIMARY KEY (day)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND default_time_to_live =  259200;

