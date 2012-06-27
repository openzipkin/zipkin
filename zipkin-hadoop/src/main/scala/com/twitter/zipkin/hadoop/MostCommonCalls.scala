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
import com.twitter.zipkin.gen.Span
import cascading.pipe.joiner.LeftJoin
import sources.{PrepSpanSource, Util}

/**
 * For each service finds the services that it most commonly calls
 */

class MostCommonCalls(args : Args) extends Job(args) with DefaultDateRangeJob {
  val preprocessed = PrepSpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'annotations))
  { s: Span => (s.id, s.parent_id, s.annotations.toList) }


  val spanInfo = preprocessed
    .flatMap('annotations -> ('cService, 'service)){ Util.getClientAndServiceName }
    .project('id, 'parent_id, 'cService, 'service)

  val idName = spanInfo
    .project('id, 'service)
    .filter('service) {n : String => n != null }
    .unique('id, 'service)
    .rename('id, 'id1)
    .rename('service, 'parentService)

  val result = spanInfo
    .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin) // dep_test_3
    .map(('cService, 'parentService) -> ('cService, 'parentService)){ n : (String, String) =>
      if (n._2 == null) {
        (n._1, n._1)
      } else n
    }
   .groupBy('service, 'parentService){ _.size('count) }
   .groupBy('service){ _.sortBy('count) }
    .write(Tsv(args("output")))
}
