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
 * Find out how often each service does memcache accesses
 */
class MemcacheRequest(args : Args) extends Job(args) with DefaultDateRangeJob {

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
    // from the annotations, find the service name
    .flatMap(('annotations, 'binary_annotations) -> ('service, 'memcacheNames)){ al : (List[Annotation], List[BinaryAnnotation]) =>
      var clientSent: Option[Annotation] = None
      al._1.foreach { a : Annotation =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) clientSent = Some(a)
      }
      // from the binary annotations, find the value of the memcache visits if there are any
    var memcachedKeys : Option[BinaryAnnotation] = None
    al._2.foreach {ba : BinaryAnnotation =>
      if (ba.key == "memcached.keys") memcachedKeys = Some(ba)
    }
    for (cs <- clientSent; key <- memcachedKeys)
      yield (cs.getHost.service_name, key.value) //Util.getArrayFromBuffer(key.value))
    }
    .project('service, 'memcacheNames)
    .groupBy('service, 'memcacheNames){ _.size('count) }
    .write(Tsv(args("output")))
}