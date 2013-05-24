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
namespace java com.twitter.zipkin.gen
namespace rb Zipkin

//********* Zipkin Aggregate Dependency Related Structs ***********


// hold statistical information about stuff.  Maps to algebird Moments
struct Moments {
  1: i64 m0,    // count
  2: double m1, // mean
  3: double m2, // variance * count
  4: double m3,
  5: double m4
}

struct DependencyLink {
  1: string parent,  // parent service name (caller)
  2: string child,   // child service name (callee)
  3: Moments durationMoments
  // histogram?
}

struct Dependencies {
  1: i64 startTime  // microseconds from epoch
  2: i64 endTime    // microseconds from epoch
  3: list<DependencyLink> links // our data
}