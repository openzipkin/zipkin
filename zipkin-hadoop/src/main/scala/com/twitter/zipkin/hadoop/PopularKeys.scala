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
import sources.{PreprocessedSpanSource, PrepSpanSource, Util}
import com.twitter.zipkin.gen.{SpanServiceName, BinaryAnnotation, Span}

/**
 * Per service, find the 100 most common keys used to annotate spans involving that service
 */
class PopularKeys(args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = PreprocessedSpanSource()
    .read
    .mapTo(0 -> ('service, 'binary_annotations))
      { s: SpanServiceName => (s.service_name, s.binary_annotations.toList) }


  val result = preprocessed
    .filter('binary_annotations){ ba : List[BinaryAnnotation] => (ba != null) && (ba.size > 0)  }
    .flatMap('binary_annotations -> 'key) { ba : List[BinaryAnnotation]  => ba.map{b: BinaryAnnotation => b.key} }
    .groupBy('service, 'key){ _.size('keyCount) }
    .groupBy('service) { _.sortBy('keyCount).reverse.take(100) }
    .write(Tsv(args("output")))

}
