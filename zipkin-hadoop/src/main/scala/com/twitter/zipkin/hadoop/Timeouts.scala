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
import com.twitter.zipkin.gen.{SpanServiceName, Annotation}
import com.twitter.zipkin.hadoop.sources.{PrepTsvSource, PreprocessedSpanSource, Util}

/**
 * Find which services timeout the most
 */

class Timeouts(args: Args) extends Job(args) with DefaultDateRangeJob {

  val ERROR_TYPE = List("finagle.timeout", "finagle.retry")

  val input = args.required("error_type")
  if (!ERROR_TYPE.contains(input)) {
    throw new IllegalArgumentException("Invalid error type : " + input)
  }

  // Preprocess the data into (trace_id, id, parent_id, annotations, client service name, service name)
  val spanInfo = PreprocessedSpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'annotations, 'service) )
      { s: SpanServiceName => (s.id, s.parent_id, s.annotations.toList, s.service_name) }


//  Project to (id, service name)
  val idName = PrepTsvSource()
    .read

  // Left join with idName to find the parent's service name, if applicable
  val result = spanInfo
    .filter('annotations){annotations : List[Annotation] => annotations.exists({a : Annotation =>  a.value == input})}
    .project('id, 'parent_id, 'service)
    .joinWithSmaller('parent_id -> 'id_1, idName, joiner = new LeftJoin)
    .map('name_1 -> 'name_1){ s: String => if (s == null) Util.UNKNOWN_SERVICE_NAME else s }
    .project('service, 'name_1)
    .groupBy('service, 'name_1){ _.size('numTimeouts) }
    .write(Tsv(args("output")))
}
