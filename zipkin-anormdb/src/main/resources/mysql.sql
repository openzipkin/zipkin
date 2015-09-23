CREATE TABLE IF NOT EXISTS zipkin_spans (
  span_id BIGINT NOT NULL,
  parent_id BIGINT,
  trace_id BIGINT NOT NULL,
  span_name VARCHAR(255) NOT NULL,
  debug SMALLINT NOT NULL,
  created_ts BIGINT
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

ALTER TABLE zipkin_spans ADD INDEX(span_id);
ALTER TABLE zipkin_spans ADD INDEX(trace_id);
ALTER TABLE zipkin_spans ADD INDEX(span_name(64));
ALTER TABLE zipkin_spans ADD INDEX(created_ts);

CREATE TABLE IF NOT EXISTS zipkin_annotations (
  span_id BIGINT NOT NULL,
  trace_id BIGINT NOT NULL,
  span_name VARCHAR(255) NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  value TEXT,
  ipv4 INT,
  port INT,
  a_timestamp BIGINT NOT NULL
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

ALTER TABLE zipkin_annotations ADD INDEX(trace_id);
ALTER TABLE zipkin_annotations ADD INDEX(span_name(64));
ALTER TABLE zipkin_annotations ADD INDEX(value(64));
ALTER TABLE zipkin_annotations ADD INDEX(a_timestamp);

CREATE TABLE IF NOT EXISTS zipkin_binary_annotations (
  span_id BIGINT NOT NULL,
  trace_id BIGINT NOT NULL,
  span_name VARCHAR(255) NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  annotation_key VARCHAR(255) NOT NULL,
  annotation_value BLOB, /* 64KB */
  annotation_type_value INT NOT NULL,
  ipv4 INT,
  port INT,
  annotation_ts BIGINT
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

ALTER TABLE zipkin_binary_annotations ADD INDEX(trace_id);
ALTER TABLE zipkin_binary_annotations ADD INDEX(span_name(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_key(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_value(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_key(64),annotation_value(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_ts);

CREATE TABLE IF NOT EXISTS zipkin_dependencies (
  dlid BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  start_ts BIGINT NOT NULL,
  end_ts BIGINT NOT NULL
) ENGINE=InnoDB; /* Not compressed as all numbers */

CREATE TABLE IF NOT EXISTS zipkin_dependency_links (
  dlid BIGINT NOT NULL,
  parent VARCHAR(255) NOT NULL,
  child VARCHAR(255) NOT NULL,
  call_count BIGINT NOT NULL
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;
