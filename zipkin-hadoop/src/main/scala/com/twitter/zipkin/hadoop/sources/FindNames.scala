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
package com.twitter.zipkin.hadoop.sources

import com.twitter.scalding._
import com.twitter.zipkin.gen._
import scala.collection.JavaConverters._

/**
 * Finds the best client side and service names for each span, if any exist
 */
class FindNames(args: Args) extends Job(args) with DefaultDateRangeJob {
  val timeGranularity: TimeGranularity = TimeGranularity.Hour

  val preprocessed = PrepNoNamesSpanSource(timeGranularity)
    .read
    .mapTo(0 ->('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations)) {
      s: Span => (s.trace_id, s.name, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList)
    }

  val findNames = preprocessed
    .flatMap('annotations -> 'service) { Util.getServiceName }
    .mapTo(('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations, 'service) -> 'spanWithServiceNames) {
      a : (Long, String, Long, Long, List[Annotation], List[BinaryAnnotation], String) =>
      a match {
        case (tid, name, id, pid, annotations, binary_annotations, service) =>
          val spanSN = new SpanServiceName(tid, name, id, annotations.asJava, binary_annotations.asJava, service)
          if (pid != 0) {
            spanSN.setParent_id(pid)
          }
          spanSN
      }
    }.write(PreprocessedSpanSource(timeGranularity))


}
