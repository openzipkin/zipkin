CREATE TABLE IF NOT EXISTS zipkin_spans (
  "trace_id_high" BIGINT NOT NULL DEFAULT 0 ,
  "trace_id" BIGINT NOT NULL,
  "id" BIGINT NOT NULL,
  "name" VARCHAR(255) NOT NULL,
  "parent_id" BIGINT,
  "debug" BOOLEAN,
  "start_ts" BIGINT ,
  "duration" BIGINT ,
   constraint KEY_zipkin_spans_trace_id_high unique("trace_id_high", "trace_id", "id")
) WITH (OIDS=FALSE);

comment on column zipkin_spans.trace_id_high is 'If non zero, this means the trace uses 128 bit traceIds instead of 64 bit';
comment on column zipkin_spans.start_ts is 'Span.timestamp(): epoch micros used for endTs query and to implement TTL';
comment on column zipkin_spans.duration is 'Span.duration(): micros used for minDuration and maxDuration query';

CREATE INDEX zipkin_spans_index_trace_id_high_trace_id_id ON zipkin_spans ("trace_id_high", "trace_id", "id");
CREATE INDEX zipkin_spans_index_trace_id_high_trace_id ON zipkin_spans ("trace_id_high", "trace_id");
CREATE INDEX zipkin_spans_index_name ON zipkin_spans ("name");
CREATE INDEX zipkin_spans_index_start_ts ON zipkin_spans ("start_ts");



CREATE TABLE IF NOT EXISTS zipkin_annotations (
  "trace_id_high" BIGINT NOT NULL DEFAULT 0,
  "trace_id" BIGINT NOT NULL,
  "span_id" BIGINT NOT NULL,
  "a_key" VARCHAR(255) NOT NULL,
  "a_value" bytea,
  "a_type" INT NOT NULL,
  "a_timestamp" BIGINT,
  "endpoint_ipv4" INT ,
  "endpoint_ipv6" bytea ,
  "endpoint_port" SMALLINT ,
  "endpoint_service_name" VARCHAR(255),
   constraint KEY_zipkin_annotations_trace_id_high unique("trace_id_high", "trace_id", "span_id", "a_key", "a_timestamp")
) WITH (OIDS=FALSE);

comment on column zipkin_annotations.trace_id_high is 'If non zero, this means the trace uses 128 bit traceIds instead of 64 bit';
comment on column zipkin_annotations.trace_id is 'coincides with zipkin_spans.trace_id';
comment on column zipkin_annotations.span_id is 'coincides with zipkin_spans.id';
comment on column zipkin_annotations.a_key is 'BinaryAnnotation.key or Annotation.value if type == -1';
comment on column zipkin_annotations.a_value is 'BinaryAnnotation.value(), which must be smaller than 64KB';
comment on column zipkin_annotations.a_type is 'BinaryAnnotation.type() or -1 if Annotation';
comment on column zipkin_annotations.a_timestamp is 'Used to implement TTL; Annotation.timestamp or zipkin_spans.timestamp';
comment on column zipkin_annotations.endpoint_ipv4 is 'Null when Binary/Annotation.endpoint is null';
comment on column zipkin_annotations.endpoint_ipv6 is 'Null when Binary/Annotation.endpoint is null, or no IPv6 address';
comment on column zipkin_annotations.endpoint_port is 'Null when Binary/Annotation.endpoint is null';
comment on column zipkin_annotations.endpoint_service_name is 'Null when Binary/Annotation.endpoint is null';

CREATE INDEX zipkin_annotations_index_trace_id_high_trace_id_id1 ON zipkin_annotations ("trace_id_high", "trace_id", "span_id", "a_key", "a_timestamp");
CREATE INDEX zipkin_annotations_index_trace_id_high_trace_id2 ON zipkin_annotations ("trace_id_high", "trace_id", "span_id");
CREATE INDEX zipkin_annotations_index_trace_id_high_trace_id ON zipkin_annotations ("trace_id_high", "trace_id");
CREATE INDEX zipkin_annotations_index_endpoint_service_name ON zipkin_annotations ("endpoint_service_name");
CREATE INDEX zipkin_annotations_index_a_type ON zipkin_annotations ("a_type");
CREATE INDEX zipkin_annotations_index_a_key ON zipkin_annotations ("a_key");
CREATE INDEX zipkin_annotations_index_trace_id_span_id_a_key ON zipkin_annotations ("trace_id", "span_id", "a_key");


CREATE TABLE IF NOT EXISTS zipkin_dependencies (
  "day" DATE NOT NULL,
  "parent" VARCHAR(255) NOT NULL,
  "child" VARCHAR(255) NOT NULL,
  "call_count" BIGINT,
  "error_count" BIGINT,
  constraint KEY_zipkin_dependencies_day unique("day", "parent", "child")
) WITH (OIDS=FALSE);
