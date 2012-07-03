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
namespace java com.twitter.zipkin.gen
namespace rb Zipkin

include "zipkinCore.thrift"

//************** Structs used for hadoop jobs**************

// Stores span information, as well as extracted client name and service name
struct SpanServiceName {
  1: i64 trace_id                  // unique trace id, use for all spans in trace
  3: string name,                  // span name, rpc method for example
  4: i64 id,                       // unique span id, only used for this span
  5: optional i64 parent_id,                // parent span id
  6: list<zipkinCore.Annotation> annotations, // list of all annotations/events that occured
  8: list<zipkinCore.BinaryAnnotation> binary_annotations, // any binary annotations
  9: string service_name              // service's name
}
