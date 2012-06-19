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
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}

/**
 * Find out how often services call each other throughout the entire system
 */

class DependencyTree(args: Args) extends Job(args) with DefaultDateRangeJob {
  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
      { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
    .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
      (left._1 ++ right._1, left._2 ++ right._2)
    }
  }

  /**
   * From the preprocessed data, get the id, parent_id, and service name
   */
  val spanInfo = preprocessed
    .project('id, 'parent_id, 'annotations)
    // TODO: account for possible differences between sent and received service names
    .flatMap('annotations -> ('cService, 'sService)) { annotations: List[Annotation] =>
      var clientSend: Option[Annotation] = None
      var serverReceived: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
        if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
      }
      // only return a value if we have both annotations
      for (cs <- clientSend; sr <- serverReceived)
        yield (cs.getHost.service_name, sr.getHost.service_name)
    }.discard('annotations)

    // get (ID, ServiceName)
/*    val idName = spanInfo
      .project('id, 'sService)
      .unique('id, 'sService)
      .rename('id, 'id1)
      .rename('sService, 'parentService)

    // Join with the original on parent ID to get the parent's service name
    val spanInfoWithParent = spanInfo
      .joinWithSmaller('parent_id -> 'id1, idName)
      .groupBy('sService, 'parentService){ _.size('count) }  */
      .write(Tsv(args("output")))
}
