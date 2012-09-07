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
import com.twitter.zipkin.hadoop.sources.{PreprocessedSpanSource, TimeGranularity}
import com.twitter.zipkin.gen.{SpanServiceName, Annotation}

/**
 * Per service, find the 100 most common annotations used to annotate spans involving that service
 */
class PopularAnnotations(args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = PreprocessedSpanSource(TimeGranularity.Day)
    .read
    .mapTo(0 -> ('service, 'annotations))
  { s: SpanServiceName => (s.service_name, s.annotations.toList) }


  val result = preprocessed
    .filter('annotations){ al : List[Annotation] => (al != null) && (al.size > 0)  }
    .flatMap('annotations -> 'value) { ba : List[Annotation]  => ba.map{b: Annotation => b.value} }
    .groupBy('service, 'value){ _.size('keyCount) }
    // TODO Kinda hacky
    .filter('keyCount) { count : Int => count > 1 }
    .groupBy('service) { _.sortBy('keyCount).reverse.take(100) }
    .discard('keyCount)
    .write(Tsv(args("output")))

}
