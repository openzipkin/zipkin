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

#************** Common annotation values **************
const string CLIENT_SEND = "cs"
const string CLIENT_RECV = "cr"
const string SERVER_SEND = "ss"
const string SERVER_RECV = "sr"
const string WIRE_SEND = "ws"
const string WIRE_RECV = "wr"

#************** Common binary annotation keys **************
// The endpoint associated with CLIENT_ annotations is not necessarily Annotation.host
const string CLIENT_ADDR = "ca"
// The endpoint associated with SERVER_ annotations is not necessarily Annotation.host
const string SERVER_ADDR = "sa"


// Indicates the network context of a service recording an annotation.
struct Endpoint {
  // IPv4 host address packed into 4 bytes.
  // Ex for the ip 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
  1: i32 ipv4
  // IPv4 port
  // Note: this is to be treated as an unsigned integer, so watch for negatives.
  2: i16 port
  // Service name, such as "memcache" or "zipkin-web"
  // Note: Some implementations set this to "Unknown"
  3: string service_name
}

# some event took place, either one by the framework or by the user
struct Annotation {
  1: i64 timestamp                 # microseconds from epoch
  2: string value                  # what happened at the timestamp?
  // The endpoint that recorded this annotation
  3: optional Endpoint host
  # 4: optional i32 OBSOLETE_duration         # how long did the operation take? microseconds
}

enum AnnotationType { BOOL, BYTES, I16, I32, I64, DOUBLE, STRING }

struct BinaryAnnotation {
  1: string key,
  2: binary value,
  3: AnnotationType annotation_type,
  // The endpoint that recorded this annotation
  4: optional Endpoint host
}

struct Span {
  1: i64 trace_id                  # unique trace id, use for all spans in trace
  3: string name,                  # span name, rpc method for example
  4: i64 id,                       # unique span id, only used for this span
  5: optional i64 parent_id,       # parent span id
  6: list<Annotation> annotations, # all annotations/events that occured, sorted by timestamp
  8: list<BinaryAnnotation> binary_annotations # any binary annotations
  9: optional bool debug = 0       # if true, we DEMAND that this span passes all samplers
}

