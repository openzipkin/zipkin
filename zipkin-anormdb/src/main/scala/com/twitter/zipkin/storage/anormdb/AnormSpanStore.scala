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
import com.twitter.util._
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import com.twitter.zipkin.storage.anormdb.AnormThreads._
import com.twitter.zipkin.storage.{IndexedTraceId, SpanStore, TraceIdDuration}
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import java.sql.Connection
import com.twitter.zipkin.storage.anormdb.DB.byteArrayToStatement

class AnormSpanStore(val db: DB, val openCon: Option[Connection] = None) extends SpanStore with DBPool {

  override def apply(spans: Seq[Span]) = Future.join(spans.map(storeSpan))

  private [this] def storeSpan(span: Span): Future[Unit] = inNewThread {
    val createdTs: Option[Long] = span.firstAnnotation match {
      case Some(anno) => Some(anno.timestamp)
      case None => None
    }

    implicit val (conn, borrowTime) = borrowConn()
    try {

      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        // Update our inventory of known span names per service
        span.serviceNames.foreach(serviceName =>
          SQL(
            db.getSpanInsertCommand() +
              """ INTO zipkin_service_spans
                |  (service_name, span_name, last_span_id, last_span_ts)
                |VALUES
                |  ({service_name}, {span_name}, {last_span_id}, {last_span_ts})
              """.stripMargin)
            .on("service_name" -> serviceName)
            .on("span_name" -> span.name)
            .on("last_span_id" -> span.id)
            .on("last_span_ts" -> createdTs)
            .execute()
        )

        SQL(
          db.getSpanInsertCommand() +
            """ INTO zipkin_spans
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
              |    annotation_value, annotation_type_value, ipv4, port, annotation_ts)
              |VALUES
              |  ({span_id}, {trace_id}, {span_name}, {service_name}, {key}, {value},
              |    {annotation_type_value}, {ipv4}, {port}, {annotation_ts})
            """.stripMargin)
            .on("span_id" -> span.id)
            .on("trace_id" -> span.traceId)
            .on("span_name" -> span.name)
            .on("service_name" -> b.host.map(_.serviceName).getOrElse("Unknown service name")) // from Annotation
            .on("key" -> b.key)
            .on("value" -> Util.getArrayFromBuffer(b.value))
            .on("annotation_type_value" -> b.annotationType.value)
            .on("ipv4" -> b.host.map(_.ipv4))
            .on("port" -> b.host.map(_.port))
            .on("annotation_ts" -> createdTs)
            .execute()
        )
      })

    } finally {
      returnConn(conn, borrowTime, "storeSpan")
    }
  }

  override def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = Future.Done

  override def getTimeToLive(traceId: Long): Future[Duration] = Future.value(Duration.Top)

  override def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      SQL(
        "SELECT trace_id FROM zipkin_spans WHERE trace_id IN (%s)".format(traceIds.mkString(","))
      ).as(long("trace_id") *).toSet

    } finally {
      returnConn(conn, borrowTime, "tracesExist")
    }
  }

  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

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
          """SELECT span_id, trace_id, span_name, service_name, value, ipv4, port, a_timestamp, duration
            |FROM zipkin_annotations
            |WHERE trace_id IN (%s)
          """.stripMargin.format(traceIdsString))
          .as((long("span_id") ~ long("trace_id") ~ str("span_name") ~ str("service_name") ~ str("value") ~
          get[Option[Int]]("ipv4") ~ get[Option[Int]]("port") ~
          long("a_timestamp") ~ get[Option[Long]]("duration") map {
          case a~b~c~d~e~f~g~h~i => DBAnnotation(a, b, c, d, e, f, g, h, i)
        }) *)
      val binAnnos:List[DBBinaryAnnotation] =
        SQL(
          """SELECT span_id, trace_id, span_name, service_name, annotation_key,
            |  annotation_value, annotation_type_value, ipv4, port
            |FROM zipkin_binary_annotations
            |WHERE trace_id IN (%s)
          """.stripMargin.format(traceIdsString))
          .as((long("span_id") ~ long("trace_id") ~ str("span_name") ~ str("service_name") ~
          str("annotation_key") ~ db.bytes("annotation_value") ~
          int("annotation_type_value") ~ get[Option[Int]]("ipv4") ~
          get[Option[Int]]("port") map {
          case a~b~c~d~e~f~g~h~i => DBBinaryAnnotation(a, b, c, d, e, f, g, h, i)
        }) *)

      val results: Seq[Seq[Span]] = traceIds.map { traceId =>
        spans.filter(_.traceId == traceId).map { span =>
          val spanAnnos = annos.filter { a =>
            a.traceId == span.traceId && a.spanId == span.spanId && a.spanName == span.spanName
          }
            .map { anno =>
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
          val spanBinAnnos = binAnnos.filter { a =>
            a.traceId == span.traceId && a.spanId == span.spanId && a.spanName == span.spanName
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
          Span(traceId, span.spanName, span.spanId, span.parentId, spanAnnos, spanBinAnnos, span.debug)
        }
      }
      results.filter(!_.isEmpty)

    } finally {
      returnConn(conn, borrowTime, "getSpansByTraceIds")
    }
  }

  override def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    getSpansByTraceIds(Seq(traceId)).map(_.head)

  override def getTraceIdsByName(serviceName: String, spanName: Option[String],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {

    if (endTs <= 0 || limit <= 0) {
      Seq.empty
    }
    else {
      implicit val (conn, borrowTime) = borrowConn()
      try {
        val result:List[(Long, Long)] = SQL(
          """SELECT t1.trace_id, MAX(a_timestamp)
            |FROM zipkin_annotations t1
            |INNER JOIN (
            |  SELECT DISTINCT trace_id
            |  FROM zipkin_annotations
            |  WHERE service_name = {service_name}
            |    AND (span_name = {span_name} OR {span_name} = '')
            |    AND a_timestamp < {end_ts}
            |  ORDER BY a_timestamp DESC
            |  LIMIT {limit})
            |AS t2 ON t1.trace_id = t2.trace_id
            |GROUP BY t1.trace_id
            |ORDER BY t1.a_timestamp DESC
          """.stripMargin)
          .on("service_name" -> serviceName)
          .on("span_name" -> (if (spanName.isEmpty) "" else spanName.get))
          .on("end_ts" -> endTs)
          .on("limit" -> limit)
          .as((long("trace_id") ~ long("MAX(a_timestamp)") map flatten) *)
        result map { case (tId, ts) =>
          IndexedTraceId(traceId = tId, timestamp = ts)
        }
      } finally {
        returnConn(conn, borrowTime, "getTraceIdsByName")
      }
    }
  }
  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = db.inNewThreadWithRecoverableRetry {
    if ((Constants.CoreAnnotations ++ Constants.CoreAddress).contains(annotation) || endTs <= 0 || limit <= 0) {
      Seq.empty
    }
    else {
      implicit val (conn, borrowTime) = borrowConn()
      try {

        val result:List[(Long, Long)] = value match {
          // Binary annotations
          case Some(bytes) => {
            SQL(
              """SELECT t1.trace_id, t1.created_ts
              |FROM zipkin_spans t1
              |INNER JOIN (
              |  SELECT DISTINCT trace_id
              |  FROM zipkin_binary_annotations
              |  WHERE service_name = {service_name}
              |    AND annotation_key = {annotation}
              |    AND annotation_value = {value}
              |    AND annotation_ts < {end_ts}
              |    AND annotation_ts IS NOT NULL
              |  ORDER BY annotation_ts DESC
              |  LIMIT {limit})
              |AS t2 ON t1.trace_id = t2.trace_id
              |GROUP BY t1.trace_id
              |ORDER BY t1.created_ts DESC
            """.stripMargin)
              .on("service_name" -> serviceName)
              .on("annotation" -> annotation)
              .on("value" -> Util.getArrayFromBuffer(bytes))
              .on("end_ts" -> endTs)
              .on("limit" -> limit)
              .as((long("trace_id") ~ long("created_ts") map flatten) *)
          }
          // Normal annotations
          case None => {
            SQL(
              """SELECT trace_id, MAX(a_timestamp)
                |FROM zipkin_annotations
                |WHERE service_name = {service_name}
                |  AND value = {annotation}
                |  AND a_timestamp < {end_ts}
                |GROUP BY trace_id
                |ORDER BY a_timestamp DESC
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
  }

  override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      val result:List[(Long, Long, Long)] = SQL(
        """SELECT trace_id, MIN(a_timestamp), MAX(a_timestamp)
          |FROM zipkin_annotations
          |WHERE trace_id IN (%s)
          |GROUP BY trace_id
        """.stripMargin.format(traceIds.mkString(",")))
        .as((long("trace_id") ~ long("MIN(a_timestamp)") ~ long("MAX(a_timestamp)") map flatten) *)
      result map { case (traceId, minTs, maxTs) =>
        TraceIdDuration(traceId, maxTs - minTs, minTs)
      }

    } finally {
      returnConn(conn, borrowTime, "getTracesDuration")
    }
  }

  override def getAllServiceNames: Future[Set[String]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      SQL(
        """SELECT service_name
          |FROM zipkin_service_spans
          |GROUP BY service_name
          |ORDER BY service_name ASC
        """.stripMargin)
        .as(str("service_name") *).toSet

    } finally {
      returnConn(conn, borrowTime, "getServiceNames")
    }
  }

  override def getSpanNames(service: String): Future[Set[String]] = db.inNewThreadWithRecoverableRetry {
    implicit val (conn, borrowTime) = borrowConn()
    try {

      SQL(
        """SELECT span_name
          |FROM zipkin_service_spans
          |WHERE service_name = {service} AND span_name <> ''
          |GROUP BY span_name
          |ORDER BY span_name ASC
        """.stripMargin)
        .on("service" -> service)
        .as(str("span_name") *)
        .toSet

    } finally {
      returnConn(conn, borrowTime, "getSpanNames")
    }
  }

  case class DBSpan(spanId: Long, parentId: Option[Long], traceId: Long, spanName: String, debug: Boolean)
  case class DBAnnotation(spanId: Long, traceId: Long, spanName: String, serviceName: String, value: String, ipv4: Option[Int], port: Option[Int], timestamp: Long, duration: Option[Long])
  case class DBBinaryAnnotation(spanId: Long, traceId: Long, spanName: String, serviceName: String, key: String, value: Array[Byte], annotationTypeValue: Int, ipv4: Option[Int], port: Option[Int])
}
