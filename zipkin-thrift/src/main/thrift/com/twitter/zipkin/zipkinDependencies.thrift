# Copyright 2013 Twitter Inc.
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

struct DependencyLink {
  /** parent service name (caller) */
  1: string parent
  /** child service name (callee) */
  2: string child
  # 3: Moments OBSOLETE_duration_moments
  /** calls made during the duration of this link */
  4: i64 callCount
  # histogram?
}

/* An aggregate representation of services paired with every service they call. */
struct Dependencies {
  /** milliseconds from epoch */
  1: i64 start_ts
  /** milliseconds from epoch */
  2: i64 end_ts
  3: list<DependencyLink> links
}
