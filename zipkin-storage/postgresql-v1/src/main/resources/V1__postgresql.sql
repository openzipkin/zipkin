--
-- Copyright 2015-2019 The OpenZipkin Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
-- in compliance with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software distributed under the License
-- is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
-- or implied. See the License for the specific language governing permissions and limitations under
-- the License.
--

CREATE TABLE IF NOT EXISTS zipkin_spans (
  trace_id_high BIGINT NOT NULL DEFAULT 0,
  trace_id BIGINT NOT NULL,
  id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  remote_service_name VARCHAR(255),
  parent_id BIGINT,
  debug BOOLEAN,
  start_ts BIGINT,
  duration BIGINT,
  PRIMARY KEY (trace_id_high, trace_id, id)
);
COMMENT ON COLUMN zipkin_spans.trace_id_high is 'If non zero, this means the trace uses 128 bit traceIds instead of 64 bit';
COMMENT ON COLUMN zipkin_spans.start_ts is 'Span.timestamp(): epoch micros used for endTs query and to implement TTL';
COMMENT ON COLUMN zipkin_spans.duration is 'Span.duration(): micros used for minDuration and maxDuration query';

CREATE INDEX zipkin_spans_trace_high_trace_id_idx ON zipkin_spans(trace_id_high, trace_id);
CREATE INDEX zipkin_spans_name_idx ON zipkin_spans(name);
CREATE INDEX zipkin_spans_remote_service_name_idx ON zipkin_spans(remote_service_name);
CREATE INDEX zipkin_spans_start_ts_idx ON zipkin_spans(start_ts);

COMMENT ON INDEX zipkin_spans_trace_high_trace_id_idx is 'for getTracesByIds';
COMMENT ON INDEX zipkin_spans_name_idx is 'for getTraces and getSpanNames';
COMMENT ON INDEX zipkin_spans_remote_service_name_idx is 'for getTraces and getRemoteServiceNames';
COMMENT ON INDEX zipkin_spans_start_ts_idx is 'for getTraces ordering and range';

CREATE TABLE IF NOT EXISTS zipkin_annotations (
  trace_id_high BIGINT NOT NULL DEFAULT 0,
  trace_id BIGINT NOT NULL,
  span_id BIGINT NOT NULL,
  a_key VARCHAR(255) NOT NULL,
  a_value BYTEA,
  a_type INT NOT NULL,
  a_timestamp BIGINT,
  endpoint_ipv4 INT,
  endpoint_ipv6 BYTEA,
  endpoint_port SMALLINT,
  endpoint_service_name VARCHAR(255),
  unique(trace_id_high, trace_id, span_id, a_key, a_timestamp)
);

COMMENT ON COLUMN zipkin_annotations.trace_id_high is 'If non zero, this means the trace uses 128 bit traceIds instead of 64 bit';
COMMENT ON COLUMN zipkin_annotations.trace_id is 'coincides with zipkin_spans.trace_id';
COMMENT ON COLUMN zipkin_annotations.span_id is 'coincides with zipkin_spans.id';
COMMENT ON COLUMN zipkin_annotations.a_key is 'BinaryAnnotation.key or Annotation.value if type == -1';
COMMENT ON COLUMN zipkin_annotations.a_value is 'BinaryAnnotation.value(), which must be smaller than 64KB';
COMMENT ON COLUMN zipkin_annotations.a_type is 'BinaryAnnotation.type() or -1 if Annotation';
COMMENT ON COLUMN zipkin_annotations.a_timestamp is 'Used to implement TTL; Annotation.timestamp or zipkin_spans.timestamp';
COMMENT ON COLUMN zipkin_annotations.endpoint_ipv4 is 'Null when Binary/Annotation.endpoint is null';
COMMENT ON COLUMN zipkin_annotations.endpoint_ipv6 is 'Null when Binary/Annotation.endpoint is null, or no IPv6 address';
COMMENT ON COLUMN zipkin_annotations.endpoint_port is 'Null when Binary/Annotation.endpoint is null';
COMMENT ON COLUMN zipkin_annotations.endpoint_service_name is 'Null when Binary/Annotation.endpoint is null';

CREATE INDEX zipkin_annotations_trace_id_high_id_span_idx ON zipkin_annotations (trace_id_high, trace_id, span_id); --
CREATE INDEX zipkin_annotations_trace_id_high_id_idx ON zipkin_annotations (trace_id_high, trace_id); --
CREATE INDEX zipkin_annotations_endpoint_service_name_idx ON zipkin_annotations (endpoint_service_name); -- COMMENT ;
CREATE INDEX zipkin_annotations_a_type_idx ON zipkin_annotations (a_type); --
CREATE INDEX zipkin_annotations_a_key_idx ON zipkin_annotations (a_key); --
CREATE INDEX zipkin_annotations_trace_id_span_a_key_idx ON zipkin_annotations (trace_id, span_id, a_key); --

COMMENT ON INDEX zipkin_annotations_trace_id_high_id_span_idx is 'for joining with zipkin_spans';
COMMENT ON INDEX zipkin_annotations_trace_id_high_id_idx is 'for getTraces/ByIds';
COMMENT ON INDEX zipkin_annotations_endpoint_service_name_idx is 'for getTraces and getServiceNames';
COMMENT ON INDEX zipkin_annotations_a_type_idx is 'for getTraces and autocomplete values';
COMMENT ON INDEX zipkin_annotations_a_key_idx is 'for getTraces and autocomplete values';
COMMENT ON INDEX zipkin_annotations_trace_id_span_a_key_idx is 'for dependencies job';


CREATE TABLE IF NOT EXISTS zipkin_dependencies (
  day DATE NOT NULL,
  parent VARCHAR(255) NOT NULL,
  child VARCHAR(255) NOT NULL,
  call_count BIGINT,
  error_count BIGINT,
  PRIMARY KEY (day, parent, child)
);
