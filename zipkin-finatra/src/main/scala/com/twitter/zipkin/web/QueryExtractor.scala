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

import com.twitter.finatra.Request
import com.twitter.logging.Logger
import com.twitter.util.Time
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import com.twitter.zipkin.query.{Order, QueryRequest}
import java.nio.ByteBuffer
import java.text.SimpleDateFormat

object QueryExtractor {
  val fmt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")

  /**
   * Takes a Finatra `Request` and produce the correct `QueryResponse` depending
   * on the GET parameters present
   */
  def apply(request: Request): Option[QueryRequest] = {
    val serviceName = request.params.get("serviceName")
    val spanName = request.params.get("spanName").flatMap {
      case "all" => None
      case "" => None
      case s@_ => Some(s)
    }

    /* Pull out the annotations */
    val annotations = extractParams(request, "annotations[%d]") match {
      case Nil     => None
      case seq @ _ => Some(seq)
    }

    /* Pull out the kv annotations */
    val keys = extractParams(request, "keyValueAnnotations[%d][key]")
    val values = extractParams(request, "keyValueAnnotations[%d][val]")

    val binaryAnnotations = (0 until (keys.length min values.length)).map { i =>
      BinaryAnnotation(keys(i), ByteBuffer.wrap(values(i).getBytes), AnnotationType.String, None)
    }.toSeq match {
      case Nil     => None
      case seq @ _ => Some(seq)
    }

    val endTimestamp = request.params.get("endDatetime") match {
      case Some(str) => {
        fmt.parse(str).getTime * 1000
      }
      case _ => {
        Time.now.inMicroseconds
      }
    }
    val limit = request.params.get("limit").map{ _.toInt }.getOrElse(100)
    val order = Order.DurationDesc

    serviceName.map { name =>
      QueryRequest(name, spanName, annotations, binaryAnnotations, endTimestamp, limit, order)
    }
  }

  private def extractParams(request: Request, keyFormatStr: String): Seq[String] = {
    var values = Seq.empty[String]
    var done = false
    var count = 0
    while (!done) {
      request.params.get(keyFormatStr.format(count)) match {
        case Some(v) => {
          values = values :+ v
          count += 1
        }
        case None => {
          done = true
        }
      }
    }
    values
  }
}
