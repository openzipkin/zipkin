CREATE TABLE IF NOT EXISTS zipkin_spans (
  `trace_id` BIGINT NOT NULL,
  `id` BIGINT NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `parent_id` BIGINT,
  `debug` BIT(1),
  `first_timestamp` BIGINT COMMENT 'Used to implement TTL; First Annotation.timestamp() or null'
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

ALTER TABLE zipkin_spans ADD KEY(`trace_id`, `id`) COMMENT 'ignore insert on duplicate';
ALTER TABLE zipkin_spans ADD INDEX(`trace_id`, `id`) COMMENT 'for joining with zipkin_annotations';
ALTER TABLE zipkin_spans ADD INDEX(`trace_id`) COMMENT 'for getTracesByIds';
ALTER TABLE zipkin_spans ADD INDEX(`name`) COMMENT 'for getTraces and getSpanNames';
ALTER TABLE zipkin_spans ADD INDEX(`first_timestamp`) COMMENT 'for getTraces ordering';

CREATE TABLE IF NOT EXISTS zipkin_annotations (
  `trace_id` BIGINT NOT NULL COMMENT 'coincides with zipkin_spans.trace_id',
  `span_id` BIGINT NOT NULL COMMENT 'coincides with zipkin_spans.span_id',
  `key` VARCHAR(255) NOT NULL COMMENT 'BinaryAnnotation.key() or Annotation.value() if a_type == -1',
  `value` BLOB COMMENT 'BinaryAnnotation.value(), which must be smaller than 64KB',
  `type` INT NOT NULL COMMENT 'BinaryAnnotation.type() or -1 if Annotation',
  `timestamp` BIGINT COMMENT 'Used to implement TTL; Annotation.timestamp() or zipkin_spans.timestamp_micros',
  `host_ipv4` INT COMMENT 'Null when Binary/Annotation.host() is null',
  `host_port` SMALLINT COMMENT 'Null when Binary/Annotation.host() is null',
  `host_service_name` VARCHAR(255) COMMENT 'Null when Binary/Annotation.host() is null'
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

ALTER TABLE zipkin_annotations ADD KEY(`trace_id`, `span_id`, `key`) COMMENT 'Ignore insert on duplicate';
ALTER TABLE zipkin_annotations ADD INDEX(`trace_id`, `span_id`) COMMENT 'for joining with zipkin_spans';
ALTER TABLE zipkin_annotations ADD INDEX(`trace_id`) COMMENT 'for getTraces/ByIds';
ALTER TABLE zipkin_annotations ADD INDEX(`host_service_name`) COMMENT 'for getTraces and getServiceNames';
ALTER TABLE zipkin_annotations ADD INDEX(`type`) COMMENT 'for getTraces';
ALTER TABLE zipkin_annotations ADD INDEX(`key`) COMMENT 'for getTraces';
ALTER TABLE zipkin_annotations ADD INDEX(`host_ipv4`) COMMENT 'for getTraces ordering';

CREATE TABLE IF NOT EXISTS zipkin_dependencies (
  dlid BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  start_ts BIGINT NOT NULL,
  end_ts BIGINT NOT NULL
) ENGINE=InnoDB; /* Not compressed as all numbers */

CREATE TABLE IF NOT EXISTS zipkin_dependency_links (
  dlid BIGINT NOT NULL,
  parent VARCHAR(255) NOT NULL,
  child VARCHAR(255) NOT NULL,
  m0 BIGINT NOT NULL,
  m1 DOUBLE PRECISION NOT NULL,
  m2 DOUBLE PRECISION NOT NULL,
  m3 DOUBLE PRECISION NOT NULL,
  m4 DOUBLE PRECISION NOT NULL
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED;

