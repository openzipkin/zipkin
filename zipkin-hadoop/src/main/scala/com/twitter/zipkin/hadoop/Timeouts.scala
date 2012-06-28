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
import sources.{PreprocessedSpanSource, Util}
import com.twitter.zipkin.gen.{SpanServiceName, Span, Annotation}

/**
 * Find which services timeout the most
 */

class Timeouts(args: Args) extends Job(args) with DefaultDateRangeJob {

  // TODO: Support retry as well in a way that doesn't involve messing with the code
  val ERROR_TYPE = List("finagle.timeout", "finagle.retry")

  // Preprocess the data into (trace_id, id, parent_id, annotations, client service name, service name)
  val spanInfo = PreprocessedSpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'annotations, 'cService, 'service) )
      { s: SpanServiceName => (s.id, s.parent_id, s.annotations.toList, s.client_service, s.service_name) }


  // Project to (id, service name)
  val idName = spanInfo
    .project('id, 'service)
    .filter('service) {n : String => n != null }
    .unique('id, 'service)
    .rename('id, 'id1)
    .rename('service, 'parentService) // test_4

  // Left join with idName to find the parent's service name, if applicable
  val result = spanInfo
    .filter('annotations){annotations : List[Annotation] => annotations.exists({a : Annotation =>  a.value == ERROR_TYPE(0)})}
    .project('id, 'parent_id, 'cService, 'service) // test_3
    .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin)
    .map(('parent_id, 'cService, 'parentService) -> 'parentService){ Util.getBestClientSideName }
    .project('service, 'parentService)
    .groupBy('service, 'parentService){ _.size('numTimeouts) }
    .write(Tsv(args("output")))
}
