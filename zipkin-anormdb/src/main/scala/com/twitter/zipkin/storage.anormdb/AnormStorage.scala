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

import com.twitter.zipkin.storage.Storage
import com.twitter.zipkin.common._
import com.twitter.zipkin.common.Annotation
import com.twitter.zipkin.common.BinaryAnnotation
import com.twitter.zipkin.util.Util
import com.twitter.util.{Duration, Future}
import anorm._
import anorm.SqlParser._
import java.nio.ByteBuffer

/**
 * Retrieve and store span information.
 *
 * This is one of two places where Zipkin interacts directly with the database,
 * the other one being AnormIndex.
 *
 * NOTE: We're ignoring TTL for now since unlike Cassandra and Redis, SQL
 * databases don't have that built in and it shouldn't be a big deal for most
 * sites. Several methods in this class deal with TTL and we just assume that
 * all spans will live forever.
 */
case class AnormStorage() extends Storage {
  // Database connection object
  private val conn = DB.getConnection()

  /**
   * Close the storage
   */
  def close() { conn.close() }

  /**
   * Store the span in the underlying storage for later retrieval.
   * @return a future for the operation
   */
  def storeSpan(span: Span): Future[Unit] = {
    SQL(
      """INSERT INTO zipkin_spans
        |  (span_id, parent_id, trace_id, span_name, debug, duration, created_ts)
        |VALUES
        |  ({span_id}, {parent_id}, {trace_id}, {span_name}, {debug}, {duration}, {created_ts})
      """.stripMargin)
      .on("span_id" -> span.id)
      .on("parent_id" -> span.parentId)
      .on("trace_id" -> span.traceId)
      .on("span_name" -> span.name)
      .on("debug" -> span.debug)
      .on("duration" -> span.duration)
      .on("created_ts" -> span.firstAnnotation.map(_.timestamp).headOption)
    .execute()

    span.annotations.foreach(a =>
      SQL(
        """INSERT INTO zipkin_annotations
          |  (span_id, trace_id, span_name, service_name, value, ipv4, port,
          |    timestamp, duration)
          |VALUES
          |  ({span_id}, {trace_id}, {span_name}, {service_name}, {value},
          |    {ipv4}, {port}, {timestamp}, {duration})
        """.stripMargin)
        .on("span_id" -> span.id)
        .on("trace_id" -> span.traceId)
        .on("span_name" -> span.name)
        .on("service_name" -> a.serviceName)
        .on("value" -> a.value)
        .on("ipv4" -> a.host.map(_.ipv4).headOption)
        .on("port" -> a.host.map(_.port).headOption)
        .on("timestamp" -> a.timestamp)
        .on("duration" -> a.duration)
        .execute()
    )
    span.binaryAnnotations.foreach(b =>
      SQL(
        """INSERT INTO zipkin_binary_annotations
          |  (span_id, trace_id, span_name, service_name, key, value,
          |    annotation_type_value, ipv4, port)
          |VALUES
          |  ({span_id}, {trace_id}, {span_name}, {service_name}, {key}, {value},
          |    {annotation_type_value}, {ipv4}, {port})
        """.stripMargin)
        .on("span_id" -> span.id)
        .on("trace_id" -> span.traceId)
        .on("span_name" -> span.name)
        .on("service_name" -> b.host.map(_.serviceName).getOrElse("Unknown service name")) // from Annotation
        .on("key" -> b.key)
        .on("value" -> Util.getArrayFromBuffer(b.value))
        .on("annotation_type_value" -> b.annotationType.value)
        .on("ipv4" -> b.host.map(_.ipv4).headOption)
        .on("port" -> b.host.map(_.ipv4).headOption)
        .execute()
    )
    Future.Unit
  }

  /**
   * Set the ttl of a trace. Used to store a particular trace longer than the
   * default. It must be oh so interesting!
   */
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
    Future.Unit
  }

  /**
   * Get the time to live for a specific trace.
   * If there are multiple ttl entries for one trace, pick the lowest one.
   */
  def getTimeToLive(traceId: Long): Future[Duration] = {
    Future(Duration.Top)
  }

  /**
   * Finds traces that have been stored from a list of trace IDs
   *
   * @param traceIds a List of trace IDs
   * @return a Set of those trace IDs from the list which are stored
   */
  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    Future(SQL(
      "SELECT trace_id FROM zipkin_spans WHERE trace_id IN (%s)".format(traceIds.mkString(","))
    )().toSet.map(row => row[Long]("trace_id")))
  }

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    val traceIdsString:String = traceIds.mkString(",")
    /*
     * No need to pull duration (Option[Long]) or created_ts (Option[Long])
     * because they're derived from the annotations and just stored in the spans
     * table for straightforward lookups.
     */
    val spans:List[(Long, Option[Long], Long, String, Boolean)] =
      SQL(
        """SELECT span_id, parent_id, trace_id, span_name, debug
          |FROM zipkin_spans
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((
          long("span_id") ~ long("parent_id") ~ long("trace_id") ~
            str("span_name") ~ bool("debug") ~ long("duration") ~
            long("created_ts") map flatten) *)
    val annos:List[(Long, Long, String, String, String, Option[Int], Option[Int], Long, Option[Long])] =
      SQL(
        """SELECT span_id, trace_id, span_name, service_name, value, ipv4,
          |  port, timestamp, duration
          |FROM zipkin_annotations
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((long("span_id") ~ long("trace_id") ~ str("span_name") ~
          str("service_name") ~ str("value") ~ int("ipv4") ~ int("port") ~
          long("timestamp") ~ long("duration") map flatten) *)
    val binAnnos:List[(Long, Long, String, String, String, Array[Byte], Int, Option[Int], Option[Int])] =
      SQL(
        """SELECT span_id, trace_id, span_name, service_name, key, value,
          |  annotation_type_value, ipv4, port
          |FROM zipkin_binary_annotations
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((long("span_id") ~ long("trace_id") ~ str("span_name") ~
          str("service_name") ~ str("key") ~ get[Array[Byte]]("value") ~
          int("annotation_type_value") ~ int("ipv4") ~ int("port") map flatten) *)
    Future(traceIds.map(traceId =>
      spans.filter(_._3 == traceId).map({span =>
        val spanAnnos = annos.filter(_._1 == span._1).map({anno =>
          val host:Option[Endpoint] = (anno._6, anno._7) match {
            case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, anno._4))
            case _ => None
          }
          val duration:Option[Duration] = anno._9 match {
            case Some(nanos) => Some(Duration.fromNanoseconds(nanos))
            case None => None
          }
          Annotation(anno._8, anno._5, host, duration)
        })
        val spanBinAnnos = binAnnos.filter(_._1 == span._1).map({binAnno =>
          val host:Option[Endpoint] = (binAnno._8, binAnno._9) match {
            case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, binAnno._4))
            case _ => None
          }
          val value = ByteBuffer.wrap(binAnno._6)
          val annotationType = AnnotationType.fromInt(binAnno._7)
          BinaryAnnotation(binAnno._5, value, annotationType, host)
        })
        Span(traceId, span._4, span._1, span._2, spanAnnos, spanBinAnnos, span._5)
      })
    ))
  }
  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = {
    getSpansByTraceIds(Seq(traceId)).map {
      _.head
    }
  }

  /**
   * How long do we store the data before we delete it? In seconds.
   */
  def getDataTimeToLive: Int = {
    Int.MaxValue
  }
}
