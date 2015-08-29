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

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Time, TwitterDateFormat}
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import com.twitter.zipkin.query.QueryRequest
import java.nio.ByteBuffer
import java.util.{Calendar, Date}

class QueryExtractor(defaultQueryLimit: Int) {
  val fmt = TwitterDateFormat("MM-dd-yyyy'T'HH:mm:ss.SSSZ")

  private[this] val dateFormat = TwitterDateFormat("MM-dd-yyyy")
  private[this] val timeFormat = TwitterDateFormat("HH:mm")

  def getDate(req: Request): Option[Date] =
    req.params.get("date").map(dateFormat.parse)

  def getDateStr(req: Request): String = {
    val date = getDate(req).getOrElse(Calendar.getInstance().getTime)
    dateFormat.format(date)
  }

  def getLimit(req: Request): Option[Int] = {
    req.params.get("limit").map(_.toInt)
  }

  def getLimitStr(req: Request): String = {
    getLimit(req).getOrElse(defaultQueryLimit).toString
  }

  def getTime(req: Request): Option[Date] =
    req.params.get("time").map(timeFormat.parse)

  def getTimeStr(req: Request): String = {
    val time = getTime(req).getOrElse(Calendar.getInstance().getTime)
    timeFormat.format(time)
  }

  def getTimestampStr(req: Request): String = {
    getTimestamp(req).getOrElse(Time.now.inMicroseconds).toString
  }

  def getTimestamp(req: Request): Option[Long] = {
    req.params.getLong("timestamp")
  }

  /**
   * Takes a `Request` and produces the correct `QueryRequest` depending
   * on the GET parameters present
   */
  def apply(req: Request): Option[QueryRequest] = req.params.get("serviceName") map { serviceName =>
    val spanName = req.params.get("spanName") filterNot { n => n == "all" || n == "" }

    val timestamp = getTimestamp(req).getOrElse(Time.now.inMicroseconds)

    val (annotations, binaryAnnotations) = req.params.get("annotationQuery") map { query =>
      var anns = Seq.empty[String]
      var binAnns = Seq.empty[BinaryAnnotation]

      query.split(" and ") foreach { ann =>
        ann.split("=").toList match {
          case "" :: Nil =>
          case key :: value :: Nil =>
            binAnns +:= BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, None)
          case key :: Nil =>
            anns +:= key
          case _ =>
        }
      }

      ( (if (anns.isEmpty) None else Some(anns)),
        (if (binAnns.isEmpty) None else Some(binAnns))
      )
    } getOrElse {
      (None, None)
    }

    val limit = getLimit(req).getOrElse(defaultQueryLimit)
    QueryRequest(serviceName, spanName, annotations, binaryAnnotations, timestamp, limit)
  }
}
