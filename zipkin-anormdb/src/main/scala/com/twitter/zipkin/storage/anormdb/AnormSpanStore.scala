/*
 * Copyright 2014 Twitter Inc.
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

import anorm.SqlParser._
import anorm._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util._
import com.twitter.zipkin.common._
import com.twitter.zipkin.storage.anormdb.AnormThreads._
import com.twitter.zipkin.storage.anormdb.DB.byteArrayToStatement
import com.twitter.zipkin.storage.{CollectAnnotationQueries, IndexedTraceId, SpanStore}
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import java.sql.Connection

class AnormSpanStore(val db: DB,
                     val openCon: Option[Connection] = None,
                     val stats: StatsReceiver = DefaultStatsReceiver.scope("AnormSpanStore")
                      ) extends SpanStore with CollectAnnotationQueries with DBPool {

  override def apply(spans: Seq[Span]) = Future.join(spans.map(storeSpan))

  private [this] def storeSpan(span: Span): Future[Unit] = inNewThread {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        SQL(
          db.replaceCommand() +
            """ INTO zipkin_spans
              |VALUES
              |  ({trace_id}, {id}, {name}, {parent_id}, {debug}, {start_ts})
            """.stripMargin)
          .on("trace_id" -> span.traceId)
          .on("id" -> span.id)
          .on("name" -> span.name)
          .on("parent_id" -> span.parentId)
          .on("debug" -> span.debug.map(if (_) 1 else 0))
          .on("start_ts" -> span.startTs)
          .execute()

        span.annotations.foreach(a =>
          SQL(
            db.replaceCommand() +
            """ INTO zipkin_annotations
              |  (trace_id, span_id, a_key, a_type, a_timestamp,
              |   endpoint_ipv4, endpoint_port, endpoint_service_name)
              |VALUES
              |  ({trace_id}, {span_id}, {value}, -1, {timestamp}, {ipv4}, {port}, {service_name})
            """.stripMargin)
            .on("trace_id" -> span.traceId)
            .on("span_id" -> span.id)
            .on("value" -> a.value)
            .on("timestamp" -> a.timestamp)
            .on("ipv4" -> a.host.map(_.ipv4))
            .on("port" -> a.host.map(_.port))
            .on("service_name" -> a.serviceName)
            .execute()
        )

        span.binaryAnnotations.foreach(b =>
          SQL(
            db.replaceCommand() +
            """ INTO zipkin_annotations
              |  (trace_id, span_id, a_key, a_value, a_type, a_timestamp,
              |   endpoint_ipv4, endpoint_port, endpoint_service_name)
              |VALUES
              |  ({trace_id}, {span_id}, {key}, {value}, {type}, {timestamp}, {ipv4}, {port}, {service_name})
            """.stripMargin)
            .on("trace_id" -> span.traceId)
            .on("span_id" -> span.id)
            .on("key" -> b.key)
            .on("value" -> Util.getArrayFromBuffer(b.value))
            .on("type" -> b.annotationType.value)
            .on("timestamp" -> span.endTs)
            .on("ipv4" -> b.host.map(_.ipv4))
            .on("port" -> b.host.map(_.port))
            .on("service_name" -> b.host.map(_.serviceName).getOrElse("Unknown service name")) // from Annotation
            .execute()
        )
      })

    } finally {
      returnConn(conn, borrowTime, "storeSpan")
    }
  }

  override def getTracesByIds(traceIds: Seq[Long]): Future[Seq[List[Span]]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      val traceIdsString:String = traceIds.mkString(",")
      val spans:List[DBSpan] =
        SQL(
          """SELECT DISTINCT id, parent_id, trace_id, name, debug
            |FROM zipkin_spans
            |WHERE trace_id IN (%s)
            |ORDER BY start_ts
          """.stripMargin.format(traceIdsString))
          .as((long("id") ~ get[Option[Long]]("parent_id") ~
          long("trace_id") ~ str("name") ~ get[Option[Int]]("debug") map {
          case a~b~c~d~e => DBSpan(a, b, c, d, e.map(_ > 0))
        }) *)
      val annos:List[DBAnnotation] =
        SQL(
          """SELECT DISTINCT span_id, trace_id, endpoint_service_name, a_key, endpoint_ipv4, endpoint_port, a_timestamp
            |FROM zipkin_annotations
            |WHERE trace_id IN (%s)
            |AND a_type = -1
            |ORDER BY a_timestamp
          """.stripMargin.format(traceIdsString))
          .as((long("span_id") ~ long("trace_id") ~ str("endpoint_service_name") ~ str("a_key") ~
          get[Option[Int]]("endpoint_ipv4") ~ get[Option[Int]]("endpoint_port") ~
          long("a_timestamp") map {
          case a~b~c~d~e~f~g => DBAnnotation(a, b, c, d, e, f, g)
        }) *)
      val binAnnos:List[DBBinaryAnnotation] =
        SQL(
          """SELECT DISTINCT span_id, trace_id, endpoint_service_name, a_key,
            |  a_value, a_type, endpoint_ipv4, endpoint_port
            |FROM zipkin_annotations
            |WHERE a_type != -1
            |AND trace_id IN (%s)
          """.stripMargin.format(traceIdsString))
          .as((long("span_id") ~ long("trace_id") ~ str("endpoint_service_name") ~
          str("a_key") ~ db.bytes("a_value") ~
          int("a_type") ~ get[Option[Int]]("endpoint_ipv4") ~
          get[Option[Int]]("endpoint_port") map {
          case a~b~c~d~e~f~g~h => DBBinaryAnnotation(a, b, c, d, e, f, g, h)
        }) *)

      val results = spans.map{ span => {
          val spanAnnos = annos.filter { a =>
            a.traceId == span.traceId && a.spanId == span.spanId
          }
            .map { anno =>
            val host:Option[Endpoint] = (anno.ipv4, anno.port) match {
              case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, anno.serviceName))
              case _ => None
            }
            Annotation(anno.timestamp, anno.value, host)
          }
          val spanBinAnnos = binAnnos.filter { a =>
            a.traceId == span.traceId && a.spanId == span.spanId
          }
            .map { binAnno =>
            val host:Option[Endpoint] = (binAnno.ipv4, binAnno.port) match {
              case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, binAnno.serviceName))
              case _ => None
            }
            val value = ByteBuffer.wrap(binAnno.value)
            val annotationType = AnnotationType.fromInt(binAnno.annotationTypeValue)
            BinaryAnnotation(binAnno.key, value, annotationType, host)
          }
          Span(span.traceId, span.spanName, span.spanId, span.parentId, spanAnnos, spanBinAnnos, span.debug)
        }
      }
      // Redundant sort as List.groupBy loses order of values
      results.groupBy(_.traceId).values.toList.sortBy(_.head) // sort traces by the first span
    } finally {
      returnConn(conn, borrowTime, "getSpansByTraceIds")
    }
  }

  override def getTraceIdsByName(serviceName: String, spanName: Option[String],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {

    if (endTs <= 0 || limit <= 0) {
      Seq.empty
    }
    else {
      implicit val (conn, borrowTime) = borrowConn()
      try {
        val result:List[(Long, Long)] = SQL(
          """SELECT t1.trace_id, end_ts
            |FROM zipkin_spans t1
            |INNER JOIN (
            |  SELECT DISTINCT trace_id, MAX(a_timestamp) as end_ts
            |  FROM zipkin_annotations
            |  WHERE endpoint_service_name = {service_name}
            |  GROUP BY trace_id)
            |AS t2 ON t1.trace_id = t2.trace_id
            |WHERE (name = {name} OR {name} = '')
            |GROUP BY t1.trace_id
            |ORDER BY end_ts
            |LIMIT {limit}
          """.stripMargin)
          .on("service_name" -> serviceName)
          .on("name" -> (if (spanName.isEmpty) "" else spanName.get))
          .on("end_ts" -> endTs)
          .on("limit" -> limit)
          .as((long("trace_id") ~ long("end_ts") map flatten) *)
        result map { case (tId, ts) =>
          IndexedTraceId(traceId = tId, timestamp = ts)
        }
      } finally {
        returnConn(conn, borrowTime, "getTraceIdsByName")
      }
    }
  }

  override def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {
      implicit val (conn, borrowTime) = borrowConn()
      try {
        val result:List[(Long, Long)] = value match {
          // Binary annotations
          case Some(bytes) => {
            SQL(
              """SELECT DISTINCT trace_id, MAX(a_timestamp)
                |FROM zipkin_annotations
                |WHERE endpoint_service_name = {service_name}
                |  AND a_key = {annotation}
                |  AND a_value = {value}
                |  AND a_type != -1
                |  AND a_timestamp <= {end_ts}
                |GROUP BY trace_id
                |ORDER BY a_timestamp
                |LIMIT {limit}
              """.stripMargin)
              .on("service_name" -> serviceName)
              .on("annotation" -> annotation)
              .on("value" -> Util.getArrayFromBuffer(bytes))
              .on("end_ts" -> endTs)
              .on("limit" -> limit)
              .as((long("trace_id") ~ long("MAX(a_timestamp)") map flatten) *)
          }
          // Normal annotations
          case None => {
            SQL(
              """SELECT trace_id, MAX(a_timestamp)
                |FROM zipkin_annotations
                |WHERE endpoint_service_name = {service_name}
                |  AND a_key = {annotation}
                |  AND a_type = -1
                |  AND a_timestamp <= {end_ts}
                |GROUP BY trace_id
                |ORDER BY a_timestamp
                |LIMIT {limit}
              """.stripMargin)
              .on("service_name" -> serviceName)
              .on("annotation" -> annotation)
              .on("end_ts" -> endTs)
              .on("limit" -> limit)
              .as((long("trace_id") ~ long("MAX(a_timestamp)") map flatten) *)
          }
        }
        result map { case (tId, ts) =>
          IndexedTraceId(traceId = tId, timestamp = ts)
        }

      } finally {
        returnConn(conn, borrowTime, "getTraceIdsByAnnotation")
      }
  }

  override def getAllServiceNames(): Future[Seq[String]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {
      SQL(
        """SELECT DISTINCT endpoint_service_name
          |FROM zipkin_annotations
          |GROUP BY endpoint_service_name
          |ORDER BY endpoint_service_name
        """.stripMargin)
        .as(str("endpoint_service_name") *)

    } finally {
      returnConn(conn, borrowTime, "getServiceNames")
    }
  }

  override def getSpanNames(_service: String): Future[Seq[String]] = db.inNewThreadWithRecoverableRetry {
    val service = _service.toLowerCase // service names are always lowercase!
    implicit val (conn, borrowTime) = borrowConn()
    try {

      SQL(
        """SELECT DISTINCT name
          |FROM zipkin_spans t1
          |JOIN zipkin_annotations t2 ON (t1.trace_id = t2.trace_id and t1.id = t2.span_id)
          |WHERE t2.endpoint_service_name = {service} AND name <> ''
          |GROUP BY name
          |ORDER BY name
        """.stripMargin)
        .on("service" -> service)
        .as(str("name") *)

    } finally {
      returnConn(conn, borrowTime, "getSpanNames")
    }
  }

  case class DBSpan(spanId: Long, parentId: Option[Long], traceId: Long, spanName: String, debug: Option[Boolean])
  case class DBAnnotation(spanId: Long, traceId: Long, serviceName: String, value: String, ipv4: Option[Int], port: Option[Int], timestamp: Long)
  case class DBBinaryAnnotation(spanId: Long, traceId: Long, serviceName: String, key: String, value: Array[Byte], annotationTypeValue: Int, ipv4: Option[Int], port: Option[Int])
}
