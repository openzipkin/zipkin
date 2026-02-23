CREATE TABLE IF NOT EXISTS spans
(
  trace_id            String,
  span_id             String,
  parent_id           String,

  service_name        LowCardinality(String),
  operation_name      LowCardinality(String),

  remote_service_name LowCardinality(String),
  span_kind           LowCardinality(String),

  start_time          DateTime64(6),
  duration_us         UInt64,

  tags                Map(String, String),
  status_code         LowCardinality(String)

) ENGINE = MergeTree()
PARTITION BY toDate(start_time)
ORDER BY (service_name, operation_name, start_time, trace_id);
