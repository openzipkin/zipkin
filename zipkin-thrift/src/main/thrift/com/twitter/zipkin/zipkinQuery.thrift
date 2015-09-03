# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
namespace java com.twitter.zipkin.thriftjava
#@namespace scala com.twitter.zipkin.thriftscala
namespace rb Zipkin

include "zipkinCore.thrift"
include "zipkinDependencies.thrift"

struct Trace {
  1: list<zipkinCore.Span> spans
}

exception QueryException {
  1: string msg
}

/* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
struct SpanTimestamp {
  1: string name
  2: i64 start_timestamp
  3: i64 end_timestamp
}

/* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
struct TraceSummary {
  1: i64 trace_id                  # the trace
  2: i64 start_timestamp           # start timestamp of the trace, in microseconds
  3: i64 end_timestamp             # end timestamp of the trace, in microseconds
  4: i32 duration_micro            # how long did the entire trace take? in microseconds
  # 5: map<string, i32> service_counts     # which services were involved?
  6: list<zipkinCore.Endpoint> endpoints      # which endpoints were involved?
  7: list<SpanTimestamp> span_timestamps
}

/* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
struct TraceCombo {
  1: Trace trace
  2: optional TraceSummary summary # not set if no spans in trace
  /** 3: optional TraceTimeline timeline */
  4: optional map<i64, i32> span_depths # not set if no spans in trace
}

/* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
enum Adjust { NOTHING, TIME_SKEW }

struct QueryRequest {
  1: string service_name
  2: optional string span_name
  3: optional list<string> annotations
  4: optional list<zipkinCore.BinaryAnnotation> OBSOLETE_binary_annotations
  /** results will have epoch microsecond timestamps before this value */
  5: i64 end_ts
  /** maximum entries to return before "end_ts" */
  6: i32 limit
  # 7: Order OBSOLETE_order = Order.NONE
  /** custom string annotations */
  8: optional map<string, string> binary_annotations
  9: bool adjust_clock_skew = true
}

/* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
struct QueryResponse {
  1: list<i64> trace_ids
  # 2: i64 OBSOLETE_start_ts
  # 3: i64 OBSOLETE_end_ts
}

service ZipkinQuery {

    list<Trace> getTracesByIds(
      1: list<i64> trace_ids,
      /* OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this. */
      2: list<Adjust> OBSOLETE_adjust,
      3: bool adjust_clock_skew = true) throws (1: QueryException qe);

    list<Trace> getTraces(1: QueryRequest request) throws (1: QueryException qe);

    /**
     * Fetch all the service names we have seen from now all the way back to the set ttl.
     */
    set<string> getServiceNames() throws (1: QueryException qe);

    /**
     * Get all the seen span names for a particular service, from now back until the set ttl.
     */
    set<string> getSpanNames(1: string service_name) throws (1: QueryException qe);

    /**
     * Get an aggregate representation of all services paired with every service they call in to.
     * This includes information on call counts and mean/stdDev/etc of call durations.  The two arguments
     * specify epoch time in microseconds. The end time is optional and defaults to one day after the
     * start time.
     */
    zipkinDependencies.Dependencies getDependencies(1: optional i64 start_time, 2: optional i64 end_time) throws (1: QueryException qe);

    /**
     * OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this.
     */
    QueryResponse getTraceIds(1: QueryRequest request) throws (1: QueryException qe);

    /**
     * OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this.
     */
    list<TraceSummary> getTraceSummariesByIds(1: list<i64> trace_ids, 2: list<Adjust> adjust) throws (1: QueryException qe);

    /**
     * OBSOLETE: update zipkin-web and zipkin-query to 1.4.2+ which no longer needs this.
     */
    list<TraceCombo> getTraceCombosByIds(1: list<i64> trace_ids, 2: list<Adjust> adjust) throws (1: QueryException qe);
}
