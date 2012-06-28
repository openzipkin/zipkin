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
import cascading.pipe.joiner._
import sources.{PreprocessedSpanSource, Util}
import com.twitter.zipkin.gen.{SpanServiceName, BinaryAnnotation, Span, Annotation}

/**
* Find out how often services call each other throughout the entire system
*/

class DependencyTree(args: Args) extends Job(args) with DefaultDateRangeJob {

  val spanInfo = PreprocessedSpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'cService, 'service))
      { s: SpanServiceName => (s.id, s.parent_id, s.client_service, s.service_name ) }

    // TODO: account for possible differences between sent and received service names
    val idName = spanInfo
      .project('id, 'service)
      .filter('service) {n : String => n != null }
      .unique('id, 'service)
      .rename('id, 'id1)
      .rename('service, 'parentService)

    /* Join with the original on parent ID to get the parent's service name */
    val spanInfoWithParent = spanInfo
      .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin)
      .map(('parent_id, 'cService, 'parentService) -> 'parentService){ Util.getBestClientSideName }
      .groupBy('service, 'parentService){ _.size('count) }
      .write(Tsv(args("output")))
}
