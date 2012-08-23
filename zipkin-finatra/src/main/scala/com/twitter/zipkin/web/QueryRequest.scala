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
import com.twitter.util.Time
import com.twitter.zipkin.gen
import java.text.SimpleDateFormat

object QueryRequest {
  val fmt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")

  /**
   * Takes a Finatra `Request` and produce the correct `QueryRequest` depending
   * on the GET parameters present
   *
   * Required parameters:
   * - service_name: String
   * - end_datetime: dateString that matches `fmt`
   *
   * Optional parameters:
   * - limit: Int, default 100
   *
   * Mapping (excluding above parameters):
   * (span_name)                        => Some(SpanQueryRequest)
   * (time_annotation)                  => Some(AnnotationQueryRequest)
   * (annotation_key, annotation_value) => Some(KeyValueAnnotationQueryRequest)
   *
   * (annotation_key)                   => Some(ServiceQueryRequest)
   * ()                                 => None
   */
  def apply(request: Request): Option[QueryRequest] = {
    val serviceName = request.params.get("serviceName")
    val spanName = request.params.get("spanName")
    val timeAnnotation = request.params.get("timeAnnotation")
    val annotationKey = request.params.get("annotationKey")
    val annotationValue = request.params.get("annotationValue")

    val endTimestamp = request.params.get("endDatetime") match {
      case Some(str) => {
        fmt.parse(str).getTime * 1000
      }
      case _ => {
        Time.now.inMicroseconds
      }
    }
    val limit = request.params.get("limit").map{ _.toInt }.getOrElse(100)
    val order = gen.Order.DurationDesc

    val spanQueryRequest = for (service <- serviceName; span <- spanName)
      yield span match {
        case "all" => {
          SpanQueryRequest(service, "", endTimestamp, limit, order)
        }
        case _ => {
          SpanQueryRequest(service, span, endTimestamp, limit, order)
        }
      }

    val timeAnnotationQueryRequest = for (service <- serviceName; ann <- timeAnnotation)
      yield AnnotationQueryRequest(service, ann, endTimestamp, limit, order)

    val keyValueQueryRequest = for (service <- serviceName; key <- annotationKey; value <- annotationValue)
      yield KeyValueAnnotationQueryRequest(service, key, value, endTimestamp, limit, order)

    spanQueryRequest orElse timeAnnotationQueryRequest orElse keyValueQueryRequest
  }
}

sealed trait QueryRequest
case class ServiceQueryRequest(serviceName: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class SpanQueryRequest(serviceName: String, spanName: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class AnnotationQueryRequest(serviceName: String, annotation: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class KeyValueAnnotationQueryRequest(serviceName: String, key: String, value: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
