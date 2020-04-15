/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.common.AggregatedHttpResponse;

import static com.linecorp.armeria.common.HttpStatus.OK;
import static com.linecorp.armeria.common.MediaType.JSON;

final class TestResponses {
  static final AggregatedHttpResponse VERSION_RESPONSE = AggregatedHttpResponse.of(OK, JSON, ""
    + "{\n"
    + "  \"name\" : \"PV-NhJd\",\n"
    + "  \"cluster_name\" : \"CollectorDBCluster\",\n"
    + "  \"cluster_uuid\" : \"UjZaM0fQRC6tkHINCg9y8w\",\n"
    + "  \"version\" : {\n"
    + "    \"number\" : \"6.7.0\",\n"
    + "    \"build_flavor\" : \"oss\",\n"
    + "    \"build_type\" : \"tar\",\n"
    + "    \"build_hash\" : \"8453f77\",\n"
    + "    \"build_date\" : \"2019-03-21T15:32:29.844721Z\",\n"
    + "    \"build_snapshot\" : false,\n"
    + "    \"lucene_version\" : \"7.7.0\",\n"
    + "    \"minimum_wire_compatibility_version\" : \"5.6.0\",\n"
    + "    \"minimum_index_compatibility_version\" : \"5.0.0\"\n"
    + "  },\n"
    + "  \"tagline\" : \"You Know, for Search\"\n"
    + "}");
  static final AggregatedHttpResponse YELLOW_RESPONSE = AggregatedHttpResponse.of(OK, JSON, ""
    + "{\n"
    + "  \"cluster_name\": \"CollectorDBCluster\",\n"
    + "  \"status\": \"yellow\",\n"
    + "  \"timed_out\": false,\n"
    + "  \"number_of_nodes\": 1,\n"
    + "  \"number_of_data_nodes\": 1,\n"
    + "  \"active_primary_shards\": 5,\n"
    + "  \"active_shards\": 5,\n"
    + "  \"relocating_shards\": 0,\n"
    + "  \"initializing_shards\": 0,\n"
    + "  \"unassigned_shards\": 5,\n"
    + "  \"delayed_unassigned_shards\": 0,\n"
    + "  \"number_of_pending_tasks\": 0,\n"
    + "  \"number_of_in_flight_fetch\": 0,\n"
    + "  \"task_max_waiting_in_queue_millis\": 0,\n"
    + "  \"active_shards_percent_as_number\": 50\n"
    + "}\n");
  static final AggregatedHttpResponse GREEN_RESPONSE = AggregatedHttpResponse.of(OK, JSON,
    "{\n"
      + "  \"cluster_name\": \"CollectorDBCluster\",\n"
      + "  \"status\": \"green\",\n"
      + "  \"timed_out\": false,\n"
      + "  \"number_of_nodes\": 1,\n"
      + "  \"number_of_data_nodes\": 1,\n"
      + "  \"active_primary_shards\": 5,\n"
      + "  \"active_shards\": 5,\n"
      + "  \"relocating_shards\": 0,\n"
      + "  \"initializing_shards\": 0,\n"
      + "  \"unassigned_shards\": 5,\n"
      + "  \"delayed_unassigned_shards\": 0,\n"
      + "  \"number_of_pending_tasks\": 0,\n"
      + "  \"number_of_in_flight_fetch\": 0,\n"
      + "  \"task_max_waiting_in_queue_millis\": 0,\n"
      + "  \"active_shards_percent_as_number\": 50\n"
      + "}\n");

  private TestResponses() {
  }
}
