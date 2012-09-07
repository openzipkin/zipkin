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

import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import com.twitter.zipkin.gen.{SpanServiceName, Annotation}
import com.twitter.zipkin.hadoop.sources.{TimeGranularity, PreprocessedSpanSource}

/**
 * Finds traces with duplicate trace IDs
 */

class FindDuplicateTraces(args: Args) extends Job(args) with DefaultDateRangeJob {

  val maxDuration = augmentString(args.required("maximum_duration")).toInt

  val result = PreprocessedSpanSource(TimeGranularity.Hour)
    .read
    .mapTo(0 ->('trace_id, 'annotations)) { s: SpanServiceName =>
      (s.trace_id, s.annotations.toList)
    }.flatMap('annotations -> 'first_and_last_timestamps ) {al : List[Annotation] =>
      var first : Long = if (al.length > 0) al(0).timestamp else Int.MaxValue
      var last : Long = if (al.length > 0) al(0).timestamp else -1
      al.foreach { a : Annotation =>
        val timestamp = a.timestamp
        if (timestamp < first) first = timestamp
        else if (timestamp > last) last = timestamp
      }
      if (first < Int.MaxValue && last > -1) Some(List(first, last)) else None
    }.groupBy('trace_id){ _.reduce('first_and_last_timestamps -> 'first_and_last_timestamps) { (left : List[Long], right : List[Long]) =>
        val first = if (left(0) > right(0)) right(0) else left(0)
        val last = if (left(1) > right(1)) left(1) else right(1)
        List(first, last)
      }
    }
    .filter('first_and_last_timestamps) { timestamps : List[Long] =>
      val durationInSeconds = (timestamps(1) - timestamps(0)) / 1000000
      durationInSeconds >= maxDuration
    }.project('trace_id)
    .write(Tsv(args("output")))
}
