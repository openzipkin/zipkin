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
import com.twitter.zipkin.gen._
import com.twitter.zipkin.hadoop.sources.{PreprocessedSpanSource, TimeGranularity, PrepNoNamesSpanSource}

/**
 * Find out how often each service does memcache accesses
 */
class MemcacheRequest(args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = PrepNoNamesSpanSource(TimeGranularity.Day)
    .read
    .mapTo(0 -> ('parent_id, 'binary_annotations))
      { s: Span => (s.parent_id, s.binary_annotations.toList) }


  val memcacheNames = preprocessed
    .flatMap('binary_annotations -> 'memcacheNames){ bal : List[BinaryAnnotation] =>
        // from the binary annotations, find the value of the memcache visits if there are any
        bal.find { ba : BinaryAnnotation => ba.key == "memcached.keys" }
    }
    .project('parent_id, 'memcacheNames)

  val memcacheRequesters = PreprocessedSpanSource(TimeGranularity.Day)
    .read
    .mapTo(0 -> ('trace_id, 'id, 'service))
      { s: SpanServiceName => (s.trace_id, s.id, s.service_name)}
    .joinWithSmaller('id -> 'parent_id, memcacheNames)
    .groupBy('trace_id, 'service, 'memcacheNames){ _.size('count) }
    .filter('count) { count: Int => count > 1 }
    .groupBy('service){ _.size('count) }
    .write(Tsv(args("output")))
}
