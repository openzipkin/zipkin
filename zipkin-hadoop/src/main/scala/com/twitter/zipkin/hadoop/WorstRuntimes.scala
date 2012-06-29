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
import com.twitter.zipkin.gen.{Span, Constants, Annotation}
import sources.{PrepNoNamesSpanSource}

/**
 * Obtain the IDs and the durations of the one hundred service calls which take the longest per service
 */

class WorstRuntimes(args: Args) extends Job(args) with DefaultDateRangeJob {

  val clientAnnotations = Seq(Constants.CLIENT_RECV, Constants.CLIENT_SEND)

  val preprocessed = PrepNoNamesSpanSource()
    .read
    .mapTo(0 -> ('id, 'annotations)) {
      s : Span => (s.id, s.annotations.toList)
    }

  val result = preprocessed
    .project('id, 'annotations)
    // let's find those client annotations and convert into service name and duration
    .flatMap('annotations -> ('service, 'duration)) { annotations: List[Annotation] =>
     var clientSend: Option[Annotation] = None
     var clientReceived: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
        if (Constants.CLIENT_RECV.equals(a.getValue)) clientReceived = Some(a)
      }
      // only return a value if we have both annotations
      for (cs <- clientSend; cr <- clientReceived)
        yield (cs.getHost.service_name, (cr.timestamp - cs.timestamp) / 1000)
    }.discard('annotations)
    //sort by duration, find the 100 largest
    .groupBy('service) { _.sortBy('duration).reverse.take(100)}
    .write(Tsv(args("output")))

}