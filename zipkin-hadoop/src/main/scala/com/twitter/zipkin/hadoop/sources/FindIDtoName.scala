/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.hadoop.sources

import com.twitter.scalding.{UtcDateRangeJob, Job, Args}
import com.twitter.zipkin.gen.SpanServiceName

/**
 * Finds the mapping from span ID to service name
 */
class FindIDtoName(args: Args) extends Job(args) with UtcDateRangeJob {

  val timeGranularity: TimeGranularity = TimeGranularity.Hour

  val spanInfo = PreprocessedSpanSource(timeGranularity)
    .read
    .mapTo(0 -> ('id_1, 'name_1))
      { s: SpanServiceName => (s.id, s.service_name ) }
    .filter('name_1) {n : String => n != null }
    .unique('id_1, 'name_1)
    .write(PrepTsvSource(timeGranularity))
}
