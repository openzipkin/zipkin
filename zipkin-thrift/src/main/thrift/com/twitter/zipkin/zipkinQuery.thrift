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

struct QueryRequest {
  1: string service_name
  2: optional string span_name
  3: optional list<string> annotations
  # 4: optional list<zipkinCore.BinaryAnnotation> OBSOLETE_binary_annotations
  8: optional map<string, string> binary_annotations
  /** results will have epoch microsecond timestamps before this value */
  5: i64 end_ts
  /** maximum entries to return before "end_ts" */
  6: i32 limit
  # 7: Order OBSOLETE_order = Order.NONE
  /** custom string annotations */
  9: bool adjust_clock_skew = true
}

service ZipkinQuery {

    list<Trace> getTracesByIds(
      1: list<i64> trace_ids,
      # 2: list<Adjust> OBSOLETE_adjust,
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
}
