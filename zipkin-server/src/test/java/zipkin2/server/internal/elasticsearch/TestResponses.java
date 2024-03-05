/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.common.AggregatedHttpResponse;

import static com.linecorp.armeria.common.HttpStatus.OK;
import static com.linecorp.armeria.common.MediaType.JSON;

final class TestResponses {
  static final AggregatedHttpResponse VERSION_RESPONSE = AggregatedHttpResponse.of(OK, JSON, """
    {
      "name" : "PV-NhJd",
      "cluster_name" : "CollectorDBCluster",
      "cluster_uuid" : "UjZaM0fQRC6tkHINCg9y8w",
      "version" : {
        "number" : "6.7.0",
        "build_flavor" : "oss",
        "build_type" : "tar",
        "build_hash" : "8453f77",
        "build_date" : "2019-03-21T15:32:29.844721Z",
        "build_snapshot" : false,
        "lucene_version" : "7.7.0",
        "minimum_wire_compatibility_version" : "5.6.0",
        "minimum_index_compatibility_version" : "5.0.0"
      },
      "tagline" : "You Know, for Search"
    }
    """);
  static final AggregatedHttpResponse YELLOW_RESPONSE = AggregatedHttpResponse.of(OK, JSON, """
    {
      "cluster_name": "CollectorDBCluster",
      "status": "yellow",
      "timed_out": false,
      "number_of_nodes": 1,
      "number_of_data_nodes": 1,
      "active_primary_shards": 5,
      "active_shards": 5,
      "relocating_shards": 0,
      "initializing_shards": 0,
      "unassigned_shards": 5,
      "delayed_unassigned_shards": 0,
      "number_of_pending_tasks": 0,
      "number_of_in_flight_fetch": 0,
      "task_max_waiting_in_queue_millis": 0,
      "active_shards_percent_as_number": 50
    }
    """);
  static final AggregatedHttpResponse GREEN_RESPONSE = AggregatedHttpResponse.of(OK, JSON,
    """
    {
      "cluster_name": "CollectorDBCluster",
      "status": "green",
      "timed_out": false,
      "number_of_nodes": 1,
      "number_of_data_nodes": 1,
      "active_primary_shards": 5,
      "active_shards": 5,
      "relocating_shards": 0,
      "initializing_shards": 0,
      "unassigned_shards": 5,
      "delayed_unassigned_shards": 0,
      "number_of_pending_tasks": 0,
      "number_of_in_flight_fetch": 0,
      "task_max_waiting_in_queue_millis": 0,
      "active_shards_percent_as_number": 50
    }
    """);

  private TestResponses() {
  }
}
