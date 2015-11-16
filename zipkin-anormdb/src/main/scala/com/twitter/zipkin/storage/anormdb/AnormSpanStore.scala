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
import com.twitter.zipkin.adjuster.{ApplyTimestampAndDuration, CorrectForClockSkew}
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

  override def apply(spans: Seq[Span]) = Future.join(spans
    .map(s => s.copy(annotations = s.annotations.sorted))
    .map(ApplyTimestampAndDuration.apply)
    .map(storeSpan))

  private [this] def storeSpan(span: Span): Future[Unit] = inNewThread {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        SQL(
          db.replaceCommand() +
            """ INTO zipkin_spans
              |VALUES
              |  ({trace_id}, {id}, {name}, {parent_id}, {debug}, {timestamp}, {duration})
            """.stripMargin)
          .on("trace_id" -> span.traceId)
          .on("id" -> span.id)
          .on("name" -> span.name)
          .on("parent_id" -> span.parentId)
          .on("debug" -> span.debug.map(if (_) 1 else 0))
          .on("timestamp" -> span.timestamp)
          .on("duration" -> span.duration)
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
            .on("timestamp" -> span.timestamp.getOrElse(System.currentTimeMillis() * 1000)) // fallback if we have no timestamp, yet
            .on("ipv4" -> b.host.map(_.ipv4))
            .on("port" -> b.host.map(_.port))
            .on("service_name" -> b.serviceName)
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
          """SELECT DISTINCT id, parent_id, trace_id, name, debug, start_ts, duration
            |FROM zipkin_spans
            |WHERE trace_id IN (%s)
            |ORDER BY start_ts
          """.stripMargin.format(traceIdsString))
          .as((long("id") ~ get[Option[Long]]("parent_id") ~
          long("trace_id") ~ str("name") ~ get[Option[Int]]("debug") ~
          get[Option[Long]]("start_ts") ~ get[Option[Long]]("duration")map {
          case a~b~c~d~e~f~g => DBSpan(a, b, c, d, e.map(_ > 0), f, g)
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
          Span(span.traceId, span.spanName, span.spanId, span.parentId, span.timestamp, span.duration, spanAnnos, spanBinAnnos, span.debug)
        }
      }
      // Redundant sort as List.groupBy loses order of values
      results.groupBy(_.traceId)
        .values.toList
        .map(CorrectForClockSkew)
        .map(ApplyTimestampAndDuration)
        .sortBy(_.head)(Ordering[Span].reverse) // sort descending by the first span
    } finally {
      returnConn(conn, borrowTime, "getSpansByTraceIds")
    }
  }

  // TODO: rewrite or delete anorm, as its implementation unnecessarily uses sliced queries
  val sliceQueryHeader =
    """SELECT t1.trace_id, start_ts
      |FROM zipkin_spans t1
      |JOIN zipkin_annotations t2 ON t1.trace_id = t2.trace_id AND t1.id = t2.span_id
      |WHERE t2.endpoint_service_name = {service_name}
    """.stripMargin

  val sliceQueryFooter =
    """AND start_ts BETWEEN {start_ts} AND {end_ts}
      |GROUP BY t1.trace_id
      |ORDER BY start_ts DESC
      |LIMIT {limit}
    """.stripMargin

  override def getTraceIdsByName(serviceName: String, spanName: Option[String],
    endTs: Long, lookback: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {

    if (endTs <= 0 || limit <= 0) {
      Seq.empty
    }
    else {
      implicit val (conn, borrowTime) = borrowConn()
      try {
        val result: List[(Long, Long)] = SQL(sliceQueryHeader +
          "AND (name = {name} OR {name} = '')" + sliceQueryFooter)
          .on("service_name" -> serviceName)
          .on("name" -> spanName.getOrElse(""))
          .on("start_ts" -> (endTs - lookback) * 1000)
          .on("end_ts" -> endTs * 1000)
          .on("limit" -> limit)
          .as((long("trace_id") ~ long("start_ts") map flatten) *)
        result map { case (tId, ts) =>
          IndexedTraceId(traceId = tId, timestamp = ts)
        }
      } finally {
        returnConn(conn, borrowTime, "getTraceIdsByName")
      }
    }
  }

  override def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, lookback: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {
      implicit val (conn, borrowTime) = borrowConn()
      try {
        val result:List[(Long, Long)] = value match {
          // Binary annotations
          case Some(bytes) => {
            SQL(sliceQueryHeader +
              """AND t2.a_key = {annotation}
                |AND t2.a_value = {value}
                |AND t2.a_type != -1
              """.stripMargin + sliceQueryFooter)
              .on("service_name" -> serviceName)
              .on("annotation" -> annotation)
              .on("value" -> Util.getArrayFromBuffer(bytes))
              .on("start_ts" -> (endTs - lookback) * 1000)
              .on("end_ts" -> endTs * 1000)
              .on("limit" -> limit)
              .as((long("trace_id") ~ long("start_ts") map flatten) *)
          }
          // Normal annotations
          case None => {
            SQL(sliceQueryHeader +
              """AND t2.a_key = {annotation}
                |AND t2.a_type = -1
              """.stripMargin + sliceQueryFooter)
              .on("service_name" -> serviceName)
              .on("annotation" -> annotation)
              .on("start_ts" -> (endTs - lookback) * 1000)
              .on("end_ts" -> endTs * 1000)
              .on("limit" -> limit)
              .as((long("trace_id") ~ long("start_ts") map flatten) *)
          }
        }
        result map { case (tId, ts) =>
          IndexedTraceId(traceId = tId, timestamp = ts)
        }

      } finally {
        returnConn(conn, borrowTime, "getTraceIdsByAnnotation")
      }
  }

  override protected def getTraceIdsByDuration(
    serviceName: String,
    spanName: Option[String],
    minDuration: Long,
    maxDuration: Option[Long],
    endTs: Long,
    lookback: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {
      val result: List[(Long, Long)] = SQL(sliceQueryHeader +
        """AND (name = {name} OR {name} = '')
          |AND duration BETWEEN {min_duration} AND {max_duration}
        """.stripMargin + sliceQueryFooter)
        .on("name" -> spanName.getOrElse(""))
        .on("service_name" -> serviceName)
        .on("min_duration" -> minDuration)
        .on("max_duration" -> maxDuration.getOrElse(Long.MaxValue))
        .on("start_ts" -> (endTs - lookback) * 1000)
        .on("end_ts" -> endTs * 1000)
        .on("limit" -> limit)
        .as((long("trace_id") ~ long("start_ts") map flatten) *)
      result map { case (tId, ts) =>
        IndexedTraceId(traceId = tId, timestamp = ts)
      }
    } finally {
      returnConn(conn, borrowTime, "getTraceIdsByDuration")
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

  case class DBSpan(spanId: Long, parentId: Option[Long], traceId: Long, spanName: String, debug: Option[Boolean], timestamp: Option[Long], duration: Option[Long])
  case class DBAnnotation(spanId: Long, traceId: Long, serviceName: String, value: String, ipv4: Option[Int], port: Option[Int], timestamp: Long)
  case class DBBinaryAnnotation(spanId: Long, traceId: Long, serviceName: String, key: String, value: Array[Byte], annotationTypeValue: Int, ipv4: Option[Int], port: Option[Int])
}
