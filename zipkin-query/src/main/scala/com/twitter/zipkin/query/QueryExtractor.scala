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

import com.twitter.finagle.http.Request
import com.twitter.finatra.annotations.Flag
import com.twitter.util.Time
import com.twitter.zipkin.storage.QueryRequest
import javax.inject.Inject
import scala.collection.mutable
import scala.util.Try

// TODO: rewrite me into a normal finatra case class
class QueryExtractor @Inject()(@Flag("zipkin.queryService.limit") defaultQueryLimit: Int) {
  /**
   * Takes a `Request` and produces the correct `QueryRequest` depending
   * on the GET parameters present
   */
  def apply(req: Request): Try[QueryRequest] = Try {
    val serviceName = req.params.get("serviceName").getOrElse("")
    val spanName = req.params.get("spanName").flatMap(n => if (n == "all" || n == "") None else Some(n))
    val minDuration = req.params.get("minDuration").flatMap(d => if (d.isEmpty) None else Some(d.toLong))
    val maxDuration = req.params.get("maxDuration").flatMap(d => if (d.isEmpty) None else Some(d.toLong))
    val endTs = req.params.getLong("endTs").getOrElse(Time.now.inMicroseconds)
    val limit = req.params.get("limit").map(_.toInt).getOrElse(defaultQueryLimit)

    val (annotations, binaryAnnotations) = req.params.get("annotationQuery") map { query =>
      val anns = mutable.Set[String]()
      val binAnns = mutable.Set[(String, String)]()

      query.split(" and ") foreach { ann =>
        ann.split("=").toList match {
          case "" :: Nil =>
          case key :: value :: Nil => binAnns.add((key, value))
          case key :: Nil => anns.add(key)
          case _ =>
        }
      }
      (anns.toSet, binAnns.toSet)
    } getOrElse {
      (Set.empty[String], Set.empty[(String, String)])
    }
    QueryRequest(serviceName, spanName, annotations, binaryAnnotations, minDuration, maxDuration, endTs, limit)
  }
}
