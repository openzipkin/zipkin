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
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Annotation}
import sources.{Util, SpanSource}

/**
 * Per service, find the 100 most common keys used to annotate spans involving that service
 */
class PopularKeys(args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
    { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
      .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
      (left._1 ++ right._1, left._2 ++ right._2)
    }
  }

  val result = preprocessed
    .project('annotations, 'binary_annotations)
    .filter('binary_annotations){ ba : List[BinaryAnnotation] => (ba != null) && (ba.size > 0)  }
    .flatMap('binary_annotations -> 'key) { ba : List[BinaryAnnotation]  => ba.map{b: BinaryAnnotation => b.key} }
   .flatMap('annotations -> ('service)) { Util.getServiceName }
    .groupBy('service, 'key){ _.size('keyCount) }
    .groupBy('service) { _.sortBy('keyCount).reverse.take(100) }
    .write(Tsv(args("output")))

}
