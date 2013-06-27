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
package com.twitter.zipkin.storage.anormdb

import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.{Index, IndexedTraceId, TraceIdDuration}
import com.twitter.util.Future
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import anorm._
import anorm.SqlParser._

/**
 * Retrieve and store trace and span information.
 *
 * This is one of two places where Zipkin interacts directly with the database,
 * the other one being AnormStorage.
 *
 * We're ignoring TTL for now since unlike Cassandra and Redis, SQL databases
 * don't have that built in and it shouldn't be a big deal for most sites.
 *
 * Tables:
 *  - traces: trace_id, duration, end_ts
 *  - spans: trace_id, span_name, service
 *  - services: service
 *  - annotations: trace_id, annotation
 */
case class AnormIndex() extends Index {
  // Database connection object
  private val conn = DB.getConnection()

  /**
   * Close the index
   */
  def close() { conn.close() }

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByName(serviceName: String, spanName: Option[String],
                        endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    val result:List[(Long, Long)] = SQL(
      """SELECT trace_id, end_ts
        |FROM traces
        |WHERE service = {service_name}
        |  AND (span_name = {span_name} OR span_name = '')
        |  AND end_ts < {end_ts}
        |ORDER BY end_ts DESC
        |LIMIT {limit}
      """.stripMargin)
      .on("service_name" -> serviceName)
      .on("span_name" -> (if (spanName.isEmpty) "" else spanName.get))
      .on("end_ts" -> endTs)
      .on("limit" -> limit)
      .as((long("trace_id") ~ long("end_ts") map flatten) *)
    Future(result map { row =>
      IndexedTraceId(traceId = row._1, timestamp = row._2)
    })
  }

  /**
   * Get the trace ids for this annotation before endTs. If value is also
   * passed we expect both the annotation key and value to be present in index
   * for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
                              endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    // TODO
    val result:List[(Long, Long)] = value match {
      case Some(v) => {
        val valueByteArray = Util.getArrayFromBuffer(v)
        SQL(
          """SELECT trace_id, end_ts
            |FROM traces
            |WHERE service = {service_name}
            |  AND annotation_key = {annotation}
            |  AND annotation_value = {value}
            |  AND end_ts < {end_ts}
            |ORDER BY end_ts DESC
            |LIMIT {limit}
          """.stripMargin)
          .on("service_name" -> serviceName)
          .on("annotation" -> annotation)
          .on("value" -> valueByteArray) // TODO Confirm that ANORM 2.1+ supports BLOBs
          .on("end_ts" -> endTs)
          .on("limit" -> limit)
          .as((long("trace_id") ~ long("end_ts") map flatten) *)
      }
      case None => {
        SQL(
          """SELECT trace_id, end_ts
            |FROM traces
            |WHERE service = {service_name}
            |  AND annotation_key = {annotation}
            |  AND end_ts < {end_ts}
            |ORDER BY end_ts DESC
            |LIMIT {limit}
          """.stripMargin)
          .on("service_name" -> serviceName)
          .on("annotation" -> annotation)
          .on("end_ts" -> endTs)
          .on("limit" -> limit)
          .as((long("trace_id") ~ long("end_ts") map flatten) *)
      }
    }
    Future(result map { row =>
      IndexedTraceId(traceId = row._1, timestamp = row._2)
    })
  }

  /**
   * Fetch the duration or an estimate thereof from the traces.
   *
   * Duration returned in microseconds.
   */
  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
    val result:List[(Long, Long, Long)] = SQL(
      """SELECT trace_id, duration, end_ts
        |FROM traces
        |WHERE trace_id IN (%s)
        |ORDER BY end_ts DESC
      """.stripMargin.format(traceIds.mkString(",")))
      .as((long("trace_id") ~ long("duration") ~ long("end_ts") map flatten) *)
    Future(result map { row =>
      // trace ID, duration, start TS
      TraceIdDuration(row._1, row._2, row._3 - row._2)
    })
  }

  /**
   * Get all the service names.
   */
  def getServiceNames: Future[Set[String]] = {
    Future(SQL(
      "SELECT service FROM spans GROUP BY service ORDER BY service ASC"
    ).as(str("service") *).toSet)
  }

  /**
   * Get all the span names for a particular service.
   */
  def getSpanNames(service: String): Future[Set[String]] = {
    Future(SQL(
      """SELECT span_name
        |FROM spans
        |WHERE service = {service}
        |GROUP BY span_name
        |ORDER BY span_name ASC
      """.stripMargin)
      .on("service" -> service)
      .as(str("service") *)
      .toSet
    )
  }

  /**
   * Index a trace id on the service and name of a specific Span
   */
  def indexTraceIdByServiceAndName(span: Span) : Future[Unit] = {
    span.serviceNames.foreach(serviceName => {
      if (serviceName != "") SQL(
        """INSERT INTO spans (trace_id, service, span_name)
          |VALUES ({trace_id}, {service}, {span_name})
        """.stripMargin)
        .on("trace_id" -> span.traceId)
        .on("service" -> serviceName)
        .on("span_name" -> span.name.toLowerCase)
        .execute()
      else false
    })
    Future.Unit
  }

  /**
   * Index the span by the annotations attached
   */
  def indexSpanByAnnotations(span: Span) : Future[Unit] = {
    // TODO
    // INSERT INTO annotations (trace_id, annotation)
    // VALUES ({trace_id}, {annotation})
    Future.Unit // Seriously, what's supposed to go here?
  }

  /**
   * Store the service name so that we can easily find out which services have
   * been called.
   */
  def indexServiceName(span: Span) : Future[Unit] = {
    span.serviceNames.foreach(serviceName => {
      if (serviceName != "") SQL(
        """INSERT INTO services (service)
          |VALUES ({service})
        """.stripMargin)
        .on("service" -> serviceName)
        .execute()
      else false
    })
    Future.Unit
  }

  /**
   * Index the span name on the service name.
   *
   * This is so we can get a list of span names when given a service name.
   * Mainly for UI purposes
   */
  def indexSpanNameByService(span: Span) : Future[Unit] = {
    // The functionality this method provides is already provided by
    //this.indexTraceIdByServiceAndName(span)
    // and the two functions will always be called together.
    Future.Unit
  }

  /**
   * Index a span's duration. This is so we can look up the trace duration.
   */
  def indexSpanDuration(span: Span): Future[Void] = {
    span.duration foreach { duration =>
      span.lastAnnotation.map(_.timestamp) foreach { t =>
        SQL(
          """INSERT INTO traces (trace_id, duration, end_ts)
            |VALUES ({trace_id}, {duration}, {end_ts})
          """.stripMargin)
          .on("trace_id" -> span.traceId)
          .on("duration" -> duration)
          .on("end_ts" -> t)
          .execute()
      }
    }
    Future.Void
  }
}
