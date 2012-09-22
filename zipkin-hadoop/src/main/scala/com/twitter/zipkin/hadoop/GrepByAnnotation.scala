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

import com.twitter.zipkin.gen.{Annotation, Span}
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import com.twitter.zipkin.hadoop.sources.{TimeGranularity, SpanSource}

class GrepByAnnotation(args: Args) extends Job(args) with DefaultDateRangeJob {

  val grepByWord = args.required("word")

  val preprocessed =
    SpanSource(TimeGranularity.Hour)
      .read
      .mapTo(0 -> ('traceid, 'annotations)) { s: Span => (s.trace_id, s.annotations.toList) }
      .filter('annotations) { annotations: List[Annotation] =>
        !annotations.filter(p => p.value.toLowerCase().contains(grepByWord)).isEmpty
      }
      .project('traceid)
      .write(Tsv(args("output")))
}