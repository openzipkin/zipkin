/*
 * Copyright 2013 Twitter Inc.
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
import com.twitter.util.{Duration, Future, FuturePool}
import anorm._
import anorm.SqlParser._
import java.nio.ByteBuffer
import java.sql.Connection
import java.util.concurrent.Executors

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
case class AnormStorage(db: DB, openCon: Option[Connection] = None) extends Storage {
  // Database connection object
  private implicit val conn = openCon match {
    case None => db.getConnection()
    case Some(con) => con
  }

  // Cached pools automatically close threads after 60 seconds
  private val threadPool = Executors.newCachedThreadPool()
  // FuturePool for asynchronous DB access
  private val sqlFuturePool = FuturePool(threadPool)

  /**
   * Close the storage
   */
  def close() { conn.close() }

  /**
   * Store the span in the underlying storage for later retrieval.
   * @return a future for the operation
   */
  def storeSpan(span: Span): Future[Unit] = sqlFuturePool {
    val createdTs: Option[Long] = span.firstAnnotation match {
      case Some(anno) => Some(anno.timestamp)
      case None => None
    }
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
      .on("debug" -> (if (span.debug) 1 else 0))
      .on("duration" -> span.duration)
      .on("created_ts" -> createdTs)
      .execute()

    span.annotations.foreach(a =>
      SQL(
        """INSERT INTO zipkin_annotations
          |  (span_id, trace_id, span_name, service_name, value, ipv4, port,
          |    a_timestamp, duration)
          |VALUES
          |  ({span_id}, {trace_id}, {span_name}, {service_name}, {value},
          |    {ipv4}, {port}, {timestamp}, {duration})
        """.stripMargin)
        .on("span_id" -> span.id)
        .on("trace_id" -> span.traceId)
        .on("span_name" -> span.name)
        .on("service_name" -> a.serviceName)
        .on("value" -> a.value)
        .on("ipv4" -> a.host.map(_.ipv4))
        .on("port" -> a.host.map(_.port))
        .on("timestamp" -> a.timestamp)
        .on("duration" -> a.duration.map(_.inNanoseconds))
        .execute()
    )
    span.binaryAnnotations.foreach(b =>
      SQL(
        """INSERT INTO zipkin_binary_annotations
          |  (span_id, trace_id, span_name, service_name, annotation_key,
          |    annotation_value, annotation_type_value, ipv4, port)
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
        .on("ipv4" -> b.host.map(_.ipv4))
        .on("port" -> b.host.map(_.ipv4))
        .execute()
    )
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
    Future.value(Duration.Top)
  }

  /**
   * Finds traces that have been stored from a list of trace IDs
   *
   * @param traceIds a List of trace IDs
   * @return a Set of those trace IDs from the list which are stored
   */
  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = sqlFuturePool[Set[Long]] {
    SQL(
      "SELECT trace_id FROM zipkin_spans WHERE trace_id IN (%s)".format(traceIds.mkString(","))
    ).as(long("trace_id") *).toSet
  }

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = sqlFuturePool[Seq[Seq[Span]]] {
    val traceIdsString:String = traceIds.mkString(",")
    val spans:List[DBSpan] =
      SQL(
        """SELECT span_id, parent_id, trace_id, span_name, debug
          |FROM zipkin_spans
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((long("span_id") ~ get[Option[Long]]("parent_id") ~
          long("trace_id") ~ str("span_name") ~ int("debug") map {
            case a~b~c~d~e => DBSpan(a, b, c, d, e > 0)
          }) *)
    val annos:List[DBAnnotation] =
      SQL(
        """SELECT span_id, trace_id, service_name, value, ipv4, port, a_timestamp, duration
          |FROM zipkin_annotations
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((long("span_id") ~ long("trace_id") ~ str("service_name") ~ str("value") ~
          get[Option[Int]]("ipv4") ~ get[Option[Int]]("port") ~
          long("a_timestamp") ~ get[Option[Long]]("duration") map {
            case a~b~c~d~e~f~g~h => DBAnnotation(a, b, c, d, e, f, g, h)
          }) *)
    val binAnnos:List[DBBinaryAnnotation] =
      SQL(
        """SELECT span_id, trace_id, service_name, annotation_key,
          |  annotation_value, annotation_type_value, ipv4, port
          |FROM zipkin_binary_annotations
          |WHERE trace_id IN (%s)
        """.stripMargin.format(traceIdsString))
        .as((long("span_id") ~ long("trace_id") ~ str("service_name") ~
          str("annotation_key") ~ db.bytes("annotation_value") ~
          int("annotation_type_value") ~ get[Option[Int]]("ipv4") ~
          get[Option[Int]]("port") map {
            case a~b~c~d~e~f~g~h => DBBinaryAnnotation(a, b, c, d, e, f, g, h)
          }) *)

    val results: Seq[Seq[Span]] = traceIds.map { traceId =>
      spans.filter(_.traceId == traceId).map { span =>
        val spanAnnos = annos.filter(_.traceId == span.traceId).map { anno =>
          val host:Option[Endpoint] = (anno.ipv4, anno.port) match {
            case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, anno.serviceName))
            case _ => None
          }
          val duration:Option[Duration] = anno.duration match {
            case Some(nanos) => Some(Duration.fromNanoseconds(nanos))
            case None => None
          }
          Annotation(anno.timestamp, anno.value, host, duration)
        }
        val spanBinAnnos = binAnnos.filter(_.traceId == span.traceId).map { binAnno =>
          val host:Option[Endpoint] = (binAnno.ipv4, binAnno.port) match {
            case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, binAnno.serviceName))
            case _ => None
          }
          val value = ByteBuffer.wrap(binAnno.value)
          val annotationType = AnnotationType.fromInt(binAnno.annotationTypeValue)
          BinaryAnnotation(binAnno.key, value, annotationType, host)
        }
        Span(traceId, span.spanName, span.spanId, span.parentId, spanAnnos, spanBinAnnos, span.debug)
      }
    }
    results.filter(!_.isEmpty)
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

  case class DBSpan(spanId: Long, parentId: Option[Long], traceId: Long, spanName: String, debug: Boolean)
  case class DBAnnotation(spanId: Long, traceId: Long, serviceName: String, value: String, ipv4: Option[Int], port: Option[Int], timestamp: Long, duration: Option[Long])
  case class DBBinaryAnnotation(spanId: Long, traceId: Long, serviceName: String, key: String, value: Array[Byte], annotationTypeValue: Int, ipv4: Option[Int], port: Option[Int])
}
