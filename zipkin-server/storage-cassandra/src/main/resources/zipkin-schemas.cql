CREATE KEYSPACE IF NOT EXISTS zipkin
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}
  AND durable_writes = false;

CREATE TABLE IF NOT EXISTS zipkin.zipkin_span (
  trace_id text,
  span_id text,
  parent_id text,
  name text,
  duration BIGINT,
  kind text,
  TIMESTAMP BIGINT,
  local_endpoint_service_name text,
  local_endpoint_ipv4 text,
  local_endpoint_ipv6 text,
  local_endpoint_port INT,
  remote_endpoint_service_name text,
  remote_endpoint_ipv4 text,
  remote_endpoint_ipv6 text,
  remote_endpoint_port INT,
  annotations text,
  tags text,
  debug INT,
  shared INT,
  uuid_unique text,
  annotation_query text,
  PRIMARY KEY(trace_id, uuid_unique)
)
  WITH CLUSTERING ORDER BY (uuid_unique DESC)
  AND compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
  AND default_time_to_live =  604800
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND speculative_retry = '95percentile'
  AND comment = 'Primary table for holding trace data';

CREATE CUSTOM INDEX IF NOT EXISTS ON zipkin.zipkin_span (local_endpoint_service_name) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {'mode': 'PREFIX'};
CREATE CUSTOM INDEX IF NOT EXISTS ON zipkin.zipkin_span (annotation_query) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {
    'mode': 'PREFIX',
    'analyzed': 'true',
    'analyzer_class':'org.apache.cassandra.index.sasi.analyzer.DelimiterAnalyzer',
    'delimiter': '░'};

CREATE TABLE IF NOT EXISTS zipkin.zipkin_service_relation_traffic (
  id text,
  table_name text,
  service_name text,
  remote_service_name text,
  time_bucket BIGINT,
  PRIMARY KEY(service_name, remote_service_name)
)
  WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
  AND caching = {'rows_per_partition': 'ALL'}
  AND default_time_to_live =  259200
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND speculative_retry = '95percentile'
  AND comment = 'Secondary table for looking up remote service names by a service name.';

CREATE TABLE IF NOT EXISTS zipkin.zipkin_service_span_traffic (
  id text,
  table_name text,
  service_name text,
  span_name text,
  time_bucket BIGINT,
  PRIMARY KEY(service_name, span_name)
)
  WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
  AND caching = {'rows_per_partition': 'ALL'}
  AND default_time_to_live =  604800
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND speculative_retry = '95percentile'
  AND comment = 'Secondary table for looking up span names by a service name.';

CREATE TABLE IF NOT EXISTS zipkin.zipkin_service_traffic (
  id text,
  table_name text,
  service_name text,
  time_bucket BIGINT,
  PRIMARY KEY(service_name, time_bucket)
)
  WITH compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'}
  AND default_time_to_live =  604800
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND speculative_retry = '95percentile'
  AND comment = 'Secondary table for looking up all services';

CREATE TABLE IF NOT EXISTS zipkin.tag_autocomplete (
  id text,
  table_name text,
  tag_key text,
  tag_value text,
  tag_type text,
  time_bucket BIGINT,
  PRIMARY KEY(tag_key, time_bucket, tag_value)
)
  WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
  AND caching = {'rows_per_partition': 'ALL'}
  AND default_time_to_live =  259200
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND speculative_retry = '95percentile'
  AND comment = 'Secondary table for looking up span tag values for auto-complete purposes.';


CREATE TABLE IF NOT EXISTS zipkin.zipkin_trace_by_service_span (
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

CREATE CUSTOM INDEX IF NOT EXISTS ON zipkin.zipkin_trace_by_service_span (duration) USING 'org.apache.cassandra.index.sasi.SASIIndex'
   WITH OPTIONS = {'mode': 'PREFIX'};

CREATE TABLE IF NOT EXISTS zipkin.zipkin_trace_by_service_remote_service (
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

CREATE TABLE IF NOT EXISTS zipkin.zipkin_dependency (
  analyze_day  date,
  parent       text,
  child        text,
  error_count  bigint,
  call_count   bigint,
  PRIMARY KEY (analyze_day, parent, child)
)
  WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
  AND default_time_to_live =  259200
  AND gc_grace_seconds = 3600
  AND read_repair_chance = 0
  AND dclocal_read_repair_chance = 0
  AND comment = 'Holder for each days generation of zipkin2.DependencyLink';