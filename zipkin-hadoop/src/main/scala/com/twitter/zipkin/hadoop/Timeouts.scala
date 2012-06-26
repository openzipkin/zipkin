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
import cascading.pipe.joiner.LeftJoin

/**
 * Find which services timeout the most
 */

class Timeouts(args: Args) extends Job(args) with DefaultDateRangeJob {

  // TODO: Support retry as well in a way that doesn't involve messing with teh code
  val ERROR_TYPE = List("finagle.timeout", "finagle.retry")

  // Preprocess the data into (trace_id, id, parent_id, annotations, binary_annotations)
  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
     { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
      .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
        (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }

  // Find the client service name, if it is there, and the best service name we can get
  val spanInfo = preprocessed
    .flatMap('annotations -> ('cService, 'service)) { annotations: List[Annotation] =>
      var clientSend: Annotation = null
      var serviceName: Option[Annotation] = None
      var hasServRecv = false
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue)) {
          if (!hasServRecv) {
            serviceName = Some(a)
          }
          clientSend = a
        } else if (Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue)) {
          serviceName = Some(a)
          hasServRecv = true
        }
      }
      for (s <- serviceName)
      yield {
        val name = if (s.getHost == null) "Unknown Service Name" else s.getHost.service_name
        if (clientSend == null) {
          (null, name)
        } else {
          val cName = if (clientSend.getHost == null) "Unknown Service Name" else clientSend.getHost.service_name
          (cName, name)
        }
      }
  }

  // Project to (id, service name)
  val idName = spanInfo
    .project('id, 'service)
    .filter('service) {n : String => n != null }
    .unique('id, 'service)
    .rename('id, 'id1)
    .rename('service, 'parentService) // test_4

  // Left join with idName to find the parent's service name, if applicable
  val result = spanInfo
    .filter('annotations){annotations : List[Annotation] => annotations.exists({a : Annotation =>  a.value == ERROR_TYPE(0)})}
    .project('id, 'parent_id, 'cService, 'service) // test_3
    .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin)
    .map(('cService, 'parentService) -> ('cService, 'parentService)){ n : (String, String) =>
      if (n._2 == null) {
        (n._1, n._1)
      } else n
    }
    .project('service, 'parentService)
    .groupBy('service, 'parentService){ _.size('numTimeouts) }
    .write(Tsv(args("output")))
}
