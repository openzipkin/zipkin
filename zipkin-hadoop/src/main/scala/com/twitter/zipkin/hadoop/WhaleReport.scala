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

import com.twitter.zipkin.gen.{BinaryAnnotation, Constants, SpanServiceName, Annotation}
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import com.twitter.zipkin.hadoop.sources.{Util, PreprocessedSpanSource}
import java.nio.ByteBuffer


/**
 * Finds traces that have 500 Internal Service Errors and finds the spans in those traces that have retries or timeouts
 */

class WhaleReport(args: Args) extends Job(args) with DefaultDateRangeJob {

  val ERRORS = List("finagle.timeout", "finagle.retry")

  val spanInfo = PreprocessedSpanSource()
    .read
    .mapTo(0 ->('trace_id, 'id, 'service, 'annotations, 'binary_annotations))
    { s: SpanServiceName => (s.trace_id, s.id, s.service_name, s.annotations.toList, s.binary_annotations.toList)
  }

  val errorTraces = spanInfo
    .project('trace_id, 'binary_annotations)
    .filter('binary_annotations) {
    bal: List[BinaryAnnotation] =>
      bal.exists({ ba: BinaryAnnotation => {
            ba != null && ba.value != null && cleanString(ba.value) == WhaleReport.ERROR_MESSAGE
        }
      })
  }
    .project('trace_id)
    .rename('trace_id -> 'trace_id_1)

  val filtered = spanInfo
    .flatMap('annotations -> 'error) { al : List[Annotation] => { al.find { a : Annotation => ERRORS.contains(a.value) } } }
    .joinWithSmaller('trace_id -> 'trace_id_1, errorTraces)
    .discard('trace_id_1)
    .groupBy('trace_id) { _.toList[String]('service -> 'serviceList) }
    .write(Tsv(args("output")))

  // When converting from ByteBuffer to String some null values seem to be passed along, so we clean them
  private def cleanString(bb : ByteBuffer) : String = {
    val chars = (new String(Util.getArrayFromBuffer(bb))).toCharArray
    var result = ""
    for (char <- chars) {
      if (char.asInstanceOf[Int] != 0) {
        result += char
      }
    }
    result
  }
}

object WhaleReport {
  val ERROR_MESSAGE = "500 Internal Server Error"
}