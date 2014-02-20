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
package com.twitter.zipkin.web

import com.twitter.finagle.http.Request
import com.twitter.util.Time
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import com.twitter.zipkin.query.{Order, QueryRequest}
import java.nio.ByteBuffer
import java.text.SimpleDateFormat

object QueryExtractor {
  val fmt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")

  /**
   * Takes a `Request` and produces the correct `QueryRequest` depending
   * on the GET parameters present
   */
  def apply(request: Request): Option[QueryRequest] = request.params.get("serviceName") map { serviceName =>
    val spanName = request.params.get("spanName") filterNot { n => n == "all" || n == "" }

    val annotations = extractParams(request, "annotations[%d]")

    val binaryAnnotations = for {
      keys <- extractParams(request, "keyValueAnnotations[%d][key]")
      values <- extractParams(request, "keyValueAnnotations[%d][val]")
    } yield {
      keys zip(values) map { case (k, v) =>
        BinaryAnnotation(k, ByteBuffer.wrap(v.getBytes), AnnotationType.String, None)
      }
    }

    val endTimestamp = request.params.get("endDatetime") match {
      case Some(str) => fmt.parse(str).getTime * 1000
      case None => Time.now.inMicroseconds
    }

    val limit = request.params.get("limit").map(_.toInt).getOrElse(Constants.DefaultQueryLimit)
    val order = Order.DurationDesc

    QueryRequest(serviceName, spanName, annotations, binaryAnnotations, endTimestamp, limit, order)
  }

  private def extractParams(request: Request, keyFormatStr: String): Option[Seq[String]] = {
    Stream.from(0).map { n =>
      request.params.get(keyFormatStr.format(n))
    }.takeWhile(_.isDefined).toSeq.flatten match {
      case Nil => None
      case seq => Some(seq)
    }
  }
}
