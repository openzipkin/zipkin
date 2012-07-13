package com.twitter.zipkin.web

import com.twitter.zipkin.gen
import java.text.SimpleDateFormat
import com.twitter.util.Time
import com.posterous.finatra.Request

object QueryRequest {
  val fmt = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")

  def apply(request: Request): QueryRequest = {
    val serviceName = request.params("service_name")
    val endTimestamp = request.params.get("end_datetime") match {
      case Some(str) => {
        fmt.parse(str).getTime * 1000
      }
      case _ => {
        Time.now.inMicroseconds
      }
    }
    val limit = request.params.get("limit").map{ _.toInt }.getOrElse(100)
    val order = gen.Order.DurationDesc

    request.params.get("span_name") match {
      case Some("all") => {
        SpanQueryRequest(serviceName, "", endTimestamp, limit, order)
      }
      case Some(spanName) => {
        SpanQueryRequest(serviceName, spanName, endTimestamp, limit, order)
      }
      case None => {
        request.params.get("time_annotation") match {
          case Some(ann) => {
            AnnotationQueryRequest(serviceName, ann, endTimestamp, limit, order)
          }
          case None => {
            request.params.get("annotation_key") match {
              case Some(key) => {
                request.params.get("annotation_value") match {
                  case Some(value) => {
                    KeyValueAnnotationQueryRequest(serviceName, key, value, endTimestamp, limit, order)
                  }
                  case None => {
                    ServiceQueryRequest(serviceName, endTimestamp, limit, order)
                  }
                }
              }
              case None => {
                ServiceQueryRequest(serviceName, endTimestamp, limit, order)
              }
            }
          }
        }
      }
    }
  }
}

sealed trait QueryRequest
case class ServiceQueryRequest(serviceName: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class SpanQueryRequest(serviceName: String, spanName: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class AnnotationQueryRequest(serviceName: String, annotation: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
case class KeyValueAnnotationQueryRequest(serviceName: String, key: String, value: String, endTimestamp: Long, limit: Int, order: gen.Order) extends QueryRequest
