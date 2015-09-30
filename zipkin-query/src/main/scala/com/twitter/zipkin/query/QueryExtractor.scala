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
package com.twitter.zipkin.query

import com.twitter.finagle.httpx.Request
import com.twitter.finatra.annotations.Flag
import com.twitter.util.Time
import javax.inject.Inject

// TODO: rewrite me into a normal finatra case class
class QueryExtractor @Inject()(@Flag("zipkin.queryService.durationBatchSize") defaultQueryLimit: Int) {
  /**
   * Takes a `Request` and produces the correct `QueryRequest` depending
   * on the GET parameters present
   */
  def apply(req: Request): Option[QueryRequest] = req.params.get("serviceName").filterNot(_ == "") map { serviceName =>
    val spanName = req.params.get("spanName") filterNot { n => n == "all" || n == "" }

    val timestamp = req.params.getLong("timestamp").getOrElse(Time.now.inMicroseconds)

    val (annotations, binaryAnnotations) = req.params.get("annotationQuery") map { query =>
      var anns = Seq.empty[String]
      var binAnns = scala.collection.mutable.Map[String, String]()

      query.split(" and ") foreach { ann =>
        ann.split("=").toList match {
          case "" :: Nil =>
          case key :: value :: Nil =>
            binAnns += key -> value
          case key :: Nil =>
            anns +:= key
          case _ =>
        }
      }
      (anns.toList, binAnns.toMap)
    } getOrElse {
      (List.empty[String], Map.empty[String, String])
    }
    val limit = req.params.get("limit").map(_.toInt).getOrElse(defaultQueryLimit)
    QueryRequest(serviceName, spanName, annotations, binaryAnnotations, timestamp, limit)
  }
}
