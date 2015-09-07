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

/**
 * Moments is defined as below per algebird's MomentsGroup.scala
 *
 * A class to calculate the first five central moments over a sequence of Doubles.
 * Given the first five central moments, we can then calculate metrics like skewness
 * and kurtosis.
 *
 * m{i} denotes the ith central moment.
 */
struct Moments {
  /** count */
  1: i64 m0
  /** mean */
  2: double m1
  /** population variance = m2 / count, when count > 1 */
  3: optional double m2
  /** skewness = math.sqrt(count) * m3 / math.pow(m2, 1.5), when count > 2 */
  4: optional double m3
  /** kurtosis = count * m4 / math.pow(m2, 2) - 3, when count > 3 */
  5: optional double m4
}

struct DependencyLink {
  /** parent service name (caller) */
  1: string parent
  /** child service name (callee) */
  2: string child
  3: Moments duration_moments
  # histogram?
}

struct Dependencies {
  /** microseconds from epoch */
  1: i64 start_time
  /** microseconds from epoch */
  2: i64 end_time
  3: list<DependencyLink> links
}

exception DependenciesException {
  1: string msg
}

service DependencySink {

    void storeDependencies(1: Dependencies dependencies) throws (1: DependenciesException e);
}

service DependencySource {

    /**
     * Get an aggregate representation of all services paired with every service they call in to.
     * This includes information on call counts and mean/stdDev/etc of call durations.  The two arguments
     * specify epoch time in microseconds. The end time is optional and defaults to one day after the
     * start time.
     */
    Dependencies getDependencies(1: optional i64 start_time, 2: optional i64 end_time) throws (1: DependenciesException qe);
}
