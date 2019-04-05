CREATE KEYSPACE IF NOT EXISTS zipkin2
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}
  AND durable_writes = false;

CREATE TYPE IF NOT EXISTS zipkin2.endpoint (
    service      text,
    ipv4         inet,
    ipv6         inet,
    port         int,
);

CREATE TYPE IF NOT EXISTS zipkin2.annotation (
    ts bigint,
    v  text,
);

CREATE TABLE IF NOT EXISTS zipkin2.span (
    trace_id            text, // when strictTraceId=false, only contains right-most 16 chars
    ts_uuid             timeuuid,
    id                  text,
    trace_id_high       text, // when strictTraceId=false, contains left-most 16 chars if present
    parent_id           text,
    kind                text,
    span                text, // span.name
    ts                  bigint,
    duration            bigint,
    l_ep                Endpoint,
    r_ep                Endpoint,
    annotations         list<frozen<annotation>>,
    tags                map<text,text>,
    shared              boolean,
    debug               boolean,
    PRIMARY KEY (trace_id, ts_uuid, id)
)
    WITH CLUSTERING ORDER BY (ts_uuid DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
    AND default_time_to_live =  604800
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0.0
    AND speculative_retry = '95percentile'
    AND comment = 'Primary table for holding trace data';

CREATE TABLE IF NOT EXISTS zipkin2.dependency (
    day          date,
    parent       text,
    child        text,
    errors       bigint,
    calls        bigint,
    PRIMARY KEY (day, parent, child)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND comment = 'Holder for each days generation of zipkin2.DependencyLink';

CREATE TABLE IF NOT EXISTS zipkin2.autocomplete_tags (
    key     text,
    value    text,
    PRIMARY KEY (key, value)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND caching = {'rows_per_partition': 'ALL'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND speculative_retry = '95percentile'
    AND comment = 'Secondary table for looking up tag key and values for a service';

CREATE TABLE IF NOT EXISTS zipkin2.remote_service_by_service (
    service text,
    remote_service text,
    PRIMARY KEY (service, remote_service)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND caching = {'rows_per_partition': 'ALL'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND speculative_retry = '95percentile'
    AND comment = 'Secondary table for looking up remote service names by a service name.';
