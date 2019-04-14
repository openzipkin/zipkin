CREATE KEYSPACE IF NOT EXISTS stress_zipkin2 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'} AND durable_writes = false;

//-- same schema but remove all UDTs and collections (as cassandra-stress doesn't support them)

CREATE TABLE IF NOT EXISTS stress_zipkin2.span (
    trace_id            text, // when strictTraceId=false, only contains right-most 16 chars
    ts_uuid             timeuuid,
    id                  text,
    trace_id_high       text, // when strictTraceId=false, contains left-most 16 chars if present
    parent_id           text,
    kind                text,
    span                text, // span.name
    ts                  bigint,
    duration            bigint,
    shared              boolean,
    debug               boolean,
    l_service           text,
    annotation_query    text, //-- can't do SASI on set<text>: ░-joined until CASSANDRA-11182
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
CREATE CUSTOM INDEX IF NOT EXISTS ON stress_zipkin2.span (l_service) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {'mode': 'PREFIX'};
CREATE CUSTOM INDEX IF NOT EXISTS ON stress_zipkin2.span (annotation_query) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {
    'mode': 'PREFIX',
    'analyzed': 'true',
    'analyzer_class':'org.apache.cassandra.index.sasi.analyzer.DelimiterAnalyzer',
    'delimiter': '░'};

CREATE TABLE IF NOT EXISTS stress_zipkin2.trace_by_service_span (
    service       text,             //-- service name
    span          text,             //-- span name, or blank for queries without span name
    bucket        int,              //-- time bucket, calculated as ts/interval (in microseconds), for some pre-configured interval like 1 day.
    ts            timeuuid,         //-- start timestamp of the span, truncated to millisecond precision
    trace_id      text,             //-- trace ID
    duration      bigint,           //-- span duration, in milliseconds
    PRIMARY KEY ((service, span, bucket), ts)
)
   WITH CLUSTERING ORDER BY (ts DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND speculative_retry = '95percentile'
    AND comment = 'Secondary table for looking up a trace by a service, or service and span. span column may be blank (when only looking up by service). bucket column adds time bucketing to the partition key, values are microseconds rounded to a pre-configured interval (typically one day). ts column is start timestamp of the span as time-uuid, truncated to millisecond precision. duration column is span duration, rounded up to tens of milliseconds (or hundredths of seconds)';
CREATE CUSTOM INDEX IF NOT EXISTS ON stress_zipkin2.trace_by_service_span (duration) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {'mode': 'PREFIX'};

CREATE TABLE IF NOT EXISTS stress_zipkin2.trace_by_service_remote_service (
    service         text,             //-- service name
    remote_service  text,             //-- remote servie name
    bucket          int,              //-- time bucket, calculated as ts/interval (in microseconds), for some pre-configured interval like 1 day.
    ts              timeuuid,         //-- start timestamp of the span, truncated to millisecond precision
    trace_id        text,             //-- trace ID
    PRIMARY KEY ((service, remote_service, bucket), ts)
)
   WITH CLUSTERING ORDER BY (ts DESC)
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND speculative_retry = '95percentile'
    AND comment = 'Secondary table for looking up a trace by a remote service. bucket column adds time bucketing to the partition key, values are microseconds rounded to a pre-configured interval (typically one day). ts column is start timestamp of the span as time-uuid, truncated to millisecond precision.';

CREATE TABLE IF NOT EXISTS stress_zipkin2.span_by_service (
    service text,
    span    text,
    PRIMARY KEY (service, span)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND caching = {'rows_per_partition': 'ALL'}
    AND default_time_to_live =  259200
    AND gc_grace_seconds = 3600
    AND read_repair_chance = 0
    AND dclocal_read_repair_chance = 0
    AND speculative_retry = '95percentile'
    AND comment = 'Secondary table for looking up span names by a service name. To compensate for hot partitions, we deduplicate write client side, use LeveledCompactionStrategy with a low threshold and add row caching.';

CREATE TABLE IF NOT EXISTS stress_zipkin2.remote_service_by_service (
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
    AND comment = 'Secondary table for looking up remote service names by a service name. To compensate for hot partitions, we deduplicate write client side, use LeveledCompactionStrategy with a low threshold and add row caching.';

CREATE TABLE IF NOT EXISTS stress_zipkin2.autocomplete_tags (
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
    AND comment = 'Secondary table for looking up span tag values for auto-complete purposes. To compensate for hot partitions, we deduplicate write client side, use LeveledCompactionStrategy with a low threshold and add row caching.';
