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
package com.twitter.zipkin.hadoop

import cascading.pipe.joiner._
import com.twitter.scalding._
import com.twitter.zipkin.gen.SpanServiceName
import com.twitter.zipkin.hadoop.sources._

/**
* Find out how often services call each other throughout the entire system
*/

class DependencyTree(args: Args) extends Job(args) with UtcDateRangeJob {

  val timeGranularity = TimeGranularity.Day

  val spanInfo = PreprocessedSpanSource(timeGranularity)
  .read
    .filter(0) { s : SpanServiceName => s.isSetParent_id() }
    .mapTo(0 -> ('id, 'parent_id, 'service))
      { s: SpanServiceName => (s.id, s.parent_id, s.service_name ) }

    // TODO: account for possible differences between sent and received service names
    val idName = PrepTsvSource(timeGranularity)
      .read
    /* Join with the original on parent ID to get the parent's service name */
    val spanInfoWithParent = spanInfo
      .joinWithSmaller('parent_id -> 'id_1, idName, joiner = new LeftJoin)
      .map('name_1 -> 'name_1){ s: String => if (s == null) Util.UNKNOWN_SERVICE_NAME else s }
      .groupBy('service, 'name_1){ _.size('count) }
      .groupBy('service){ _.sortBy('count) }
      .write(Tsv(args("output")))
}
