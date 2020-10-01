--
-- Copyright 2015-2020 The OpenZipkin Authors
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

CREATE SCHEMA IF NOT EXISTS zipkin;

CREATE TABLE IF NOT EXISTS zipkin.zipkin_spans (
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

CREATE TABLE IF NOT EXISTS zipkin.zipkin_annotations (
  trace_id_high BIGINT NOT NULL DEFAULT 0,
  trace_id BIGINT NOT NULL,
  span_id BIGINT NOT NULL,
  a_key VARCHAR(255) NOT NULL,
  a_value bytea,
  a_type INT NOT NULL,
  a_timestamp BIGINT,
  endpoint_ipv4 INT,
  endpoint_ipv6 bytea,
  endpoint_port SMALLINT,
  endpoint_service_name VARCHAR(255)
);
CREATE UNIQUE INDEX IF NOT EXISTS zipkin_annotations_unique on zipkin.zipkin_annotations (trace_id_high, trace_id, span_id, a_key, a_timestamp);

CREATE TABLE IF NOT EXISTS zipkin.zipkin_dependencies (
  day DATE NOT NULL,
  parent VARCHAR(255) NOT NULL,
  child VARCHAR(255) NOT NULL,
  call_count BIGINT,
  error_count BIGINT,
  PRIMARY KEY (day, parent, child)
);
