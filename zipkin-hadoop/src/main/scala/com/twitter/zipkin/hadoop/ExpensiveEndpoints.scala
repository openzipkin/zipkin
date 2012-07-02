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

import com.twitter.zipkin.gen.{Constants, SpanServiceName, Annotation}
import cascading.pipe.joiner.LeftJoin
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import sources.{PrepTsvSource, Util, PreprocessedSpanSource}

/**
 * Per service call (i.e. pair of services), finds the average run time (in microseconds) of that service call
 */
class ExpensiveEndpoints(args : Args) extends Job(args) with DefaultDateRangeJob {

  val spanInfo = PreprocessedSpanSource()
    .read
    .filter(0) { s : SpanServiceName => s.isSetParent_id() }
    .mapTo(0 -> ('id, 'parent_id, 'cService, 'service, 'annotations))
      { s: SpanServiceName => (s.id, s.parent_id, s.client_service, s.service_name, s.annotations.toList) }
    .flatMap('annotations -> 'duration) { al : List[Annotation] => {
        var clientSend : Option[Annotation] = None
        var clientReceive : Option[Annotation] = None
        var serverReceive : Option[Annotation] = None
        var serverSend : Option[Annotation] = None
        al.foreach( { a : Annotation => {
            if (a.getHost != null) {
              if (Constants.CLIENT_SEND.equals(a.value)) clientSend = Some(a)
              else if (Constants.CLIENT_RECV.equals(a.value)) clientReceive = Some(a)
              else if (Constants.SERVER_RECV.equals(a.value)) serverReceive = Some(a)
              else if (Constants.SERVER_SEND.equals(a.value)) serverSend = Some(a)
            }
          }
        })
        val clientDuration = for (cs <- clientSend; cr <- clientReceive) yield (cr.timestamp - cs.timestamp)
        val serverDuration = for (sr <- serverReceive; ss <- serverSend) yield (ss.timestamp - sr.timestamp)
          // to deal with the case where there is no server duration
        if (clientDuration == None) serverDuration else clientDuration
      }
    }

  val idName = PrepTsvSource()
    .read
  /* Join with the original on parent ID to get the parent's service name */
  val spanInfoWithParent = spanInfo
    .joinWithSmaller('parent_id -> 'id_1, idName)
    .groupBy('name_1, 'service){ _.average('duration) }
    .write(Tsv(args("output")))
}
