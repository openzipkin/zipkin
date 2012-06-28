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

import com.twitter.scalding._
import cascading.pipe.joiner.LeftJoin
import com.twitter.zipkin.gen.{SpanServiceName}
import sources.{PreprocessedSpanSource, Util}

/**
 * For each service finds the services that it most commonly calls
 */

class MostCommonCalls(args : Args) extends Job(args) with DefaultDateRangeJob {
  val spanInfo = PreprocessedSpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'cService, 'service))
  { s: SpanServiceName => (s.id, s.parent_id, s.client_service, s.service_name) }

  val idName = spanInfo
    .project('id, 'service)
    .filter('service) {n : String => n != null }
    .unique('id, 'service)
    .rename('id, 'id1)
    .rename('service, 'parentService)

  val result = spanInfo
    .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin)
    .map(('parent_id, 'cService, 'parentService) -> 'parentService){ Util.getBestClientSideName }
    .groupBy('service, 'parentService){ _.size('count) }
    .groupBy('service){ _.sortBy('count) }
    .write(Tsv(args("output")))
}
