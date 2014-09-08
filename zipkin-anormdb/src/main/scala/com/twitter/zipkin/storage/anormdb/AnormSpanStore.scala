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
import com.twitter.util.{Duration, Future, FuturePool, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import com.twitter.zipkin.storage.{IndexedTraceId, SpanStore, TraceIdDuration}
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import java.sql.Connection
import com.twitter.logging.Logger

// TODO: connection pooling for real parallelism
class AnormSpanStore(
  db: SpanStoreDB,
  openCon: Option[Connection] = None,
  pool: FuturePool = FuturePool.unboundedPool
) extends SpanStore {
  // Database connection object
  private[this] implicit val conn = openCon match {
    case None => db.getConnection()
    case Some(con) => con
  }

  def close(deadline: Time): Future[Unit] = pool {
    conn.close()
  }

  private[this] val spanInsertSql = SQL("""
    |INSERT INTO zipkin_spans
    |  (span_id, parent_id, trace_id, span_name, debug, duration, created_ts)
    |VALUES
    |  ({span_id}, {parent_id}, {trace_id}, {span_name}, {debug}, {duration}, {created_ts})
  """.stripMargin).asBatch

  private[this] val annInsertSql = SQL("""
    |INSERT INTO zipkin_annotations
    |  (span_id, trace_id, span_name, service_name, value, ipv4, port,
    |    a_timestamp, duration)
    |VALUES
    |  ({span_id}, {trace_id}, {span_name}, {service_name}, {value},
    |    {ipv4}, {port}, {timestamp}, {duration})
  """.stripMargin).asBatch

  private[this] val binAnnInsertSql = SQL("""
    |INSERT INTO zipkin_binary_annotations
    |  (span_id, trace_id, span_name, service_name, annotation_key,
    |    annotation_value, annotation_type_value, ipv4, port)
    |VALUES
    |  ({span_id}, {trace_id}, {span_name}, {service_name}, {key}, {value},
    |    {annotation_type_value}, {ipv4}, {port})
  """.stripMargin).asBatch

  // store a list of spans
  def apply(spans: Seq[Span]): Future[Unit] = {
    val init = (spanInsertSql, annInsertSql, binAnnInsertSql)
    val (spanBatch, annBatch, binAnnBatch) =
      spans.foldLeft(init) { case ((sb, ab, bb), span) =>
        val sbp = sb.addBatch(
          ("span_id" -> span.id),
          ("parent_id" -> span.parentId),
          ("trace_id" -> span.traceId),
          ("span_name" -> span.name),
          ("debug" -> (if (span.debug) 1 else 0)),
          ("duration" -> span.duration),
          ("created_ts" -> span.firstAnnotation.map(_.timestamp))
        )

        if (!shouldIndex(span)) (sbp, ab, bb) else {
          val abp = span.annotations.foldLeft(ab) { (ab, a) =>
            ab.addBatch(
              ("span_id" -> span.id),
              ("trace_id" -> span.traceId),
              ("span_name" -> span.name),
              ("service_name" -> a.serviceName),
              ("value" -> a.value),
              ("ipv4" -> a.host.map(_.ipv4)),
              ("port" -> a.host.map(_.port)),
              ("timestamp" -> a.timestamp),
              ("duration" -> a.duration.map(_.inNanoseconds)))
          }

          val bbp = span.binaryAnnotations.foldLeft(bb) { (bb, b) =>
            bb.addBatch(
              ("span_id" -> span.id),
              ("trace_id" -> span.traceId),
              ("span_name" -> span.name),
              ("service_name" -> b.host.map(_.serviceName).getOrElse("unknown")), // from Annotation
              ("key" -> b.key),
              ("value" -> Util.getArrayFromBuffer(b.value)),
              ("annotation_type_value" -> b.annotationType.value),
              ("ipv4" -> b.host.map(_.ipv4)),
              ("port" -> b.host.map(_.ipv4)))
          }

          (sbp, abp, bbp)
        }
      }

    // This parallelism is a lie. There's only one DB connection (for now anyway).
    Future.join(Seq(
      pool { spanBatch.execute() },
      pool { annBatch.execute() },
      pool { binAnnBatch.execute() }
    ))
  }

  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] =
    Future.Done

  def getTimeToLive(traceId: Long): Future[Duration] =
    Future.value(Duration.Top)

  private[this] def tracesExistSql(ids: Seq[Long]) = SQL("""
    SELECT trace_id FROM zipkin_spans WHERE trace_id IN (%s)
  """.format(ids.mkString(",")))

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = pool {
    tracesExistSql(traceIds)
      .as(long("trace_id") *)
      .toSet
  }

  private[this] def ep(ipv4: Option[Int], port: Option[Int], name: String) =
    (ipv4, port) match {
      case (Some(ipv4), Some(port)) => Some(Endpoint(ipv4, port.toShort, name))
      case _ => None
    }

  private[this] def spansSql(ids: Seq[Long]) = SQL("""
    |SELECT span_id, parent_id, trace_id, span_name, debug
    |FROM zipkin_spans
    |WHERE trace_id IN (%s)
  """.stripMargin.format(ids.mkString(",")))

  private[this] val spansResults = (
    long("span_id") ~
    get[Option[Long]]("parent_id") ~
    long("trace_id") ~
    str("span_name") ~
    int("debug")
  ) map { case sId~pId~tId~sn~d =>
    Span(tId, sn, sId, pId, List.empty, List.empty, d > 0)
  }

  private[this] def annsSql(ids: Seq[Long]) = SQL("""
    |SELECT span_id, trace_id, span_name, service_name, value, ipv4, port, a_timestamp, duration
    |FROM zipkin_annotations
    |WHERE trace_id IN (%s)
  """.stripMargin.format(ids.mkString(",")))

  private[this] val annsResults = (
    long("span_id") ~
    long("trace_id") ~
    str("span_name") ~
    str("service_name") ~
    str("value") ~
    get[Option[Int]]("ipv4") ~
    get[Option[Int]]("port") ~
    long("a_timestamp") ~
    get[Option[Long]]("duration")
  ) map { case sId~tId~spN~svcN~v~ip~p~ts~d =>
    (tId, sId) -> Annotation(ts, v, ep(ip, p, svcN), d map Duration.fromNanoseconds)
  }

  private[this] def binAnnsSql(ids: Seq[Long]) = SQL("""
    |SELECT span_id, trace_id, span_name, service_name, annotation_key,
    |  annotation_value, annotation_type_value, ipv4, port
    |FROM zipkin_binary_annotations
    |WHERE trace_id IN (%s)
  """.stripMargin.format(ids.mkString(",")))

  private[this] val binAnnsResults = (
    long("span_id") ~
    long("trace_id") ~
    str("span_name") ~
    str("service_name") ~
    str("annotation_key") ~
    db.bytes("annotation_value") ~
    int("annotation_type_value") ~
    get[Option[Int]]("ipv4") ~
    get[Option[Int]]("port")
  ) map { case sId~tId~spN~svcN~key~annV~annTV~ip~p =>
    val annVal = ByteBuffer.wrap(annV)
    val annType = AnnotationType.fromInt(annTV)
    (tId, sId) -> BinaryAnnotation(key, annVal, annType, ep(ip, p, svcN))
  }

  // parallel queries here are also a lie (see above).
  def getSpansByTraceIds(ids: Seq[Long]): Future[Seq[Seq[Span]]] = {
    val spans = pool {
      spansSql(ids).as(spansResults *)
    } map { _.distinct.groupBy(_.traceId) }

    val anns = pool {
      annsSql(ids).as(annsResults *)
    } map { _.groupBy(_._1) }

    val binAnns = pool {
      binAnnsSql(ids).as(binAnnsResults *)
    } map { _.groupBy(_._1) }

    Future.join(spans, anns, binAnns) map { case (spans, anns, binAnns) =>
      ids map { id =>
        for (tSpans <- spans.get(id).toSeq; tSpan <- tSpans) yield {
          val tsAnns = anns.get((id, tSpan.id)).map(_.map(_._2)).toList.flatten
          val tsBinAnns = binAnns.get((id, tSpan.id)).map(_.map(_._2)).toList.flatten
          tSpan.copy(annotations = tsAnns, binaryAnnotations = tsBinAnns)
        }
      } filter { _.nonEmpty }
    }
  }

  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    getSpansByTraceIds(Seq(traceId)).map(_.head)

  private[this] val idsByNameSql = SQL("""
    |SELECT trace_id, MAX(a_timestamp)
    |FROM zipkin_annotations
    |WHERE service_name = {service_name}
    |  AND (span_name = {span_name} OR {span_name} = '')
    |  AND a_timestamp < {end_ts}
    |GROUP BY trace_id
    |ORDER BY a_timestamp DESC
    |LIMIT {limit}
  """.stripMargin)

  private[this] val idsByNameResults = (
    long("trace_id") ~
    long("MAX(a_timestamp)")
  ) map { case a~b => IndexedTraceId(a, b) }

  def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = pool {
    Logger.get.info("zzz SQL: " + idsByNameSql)
    idsByNameSql
      .on("service_name" -> serviceName)
      .on("span_name" -> spanName.getOrElse(""))
      .on("end_ts" -> endTs)
      .on("limit" -> limit)
      .as(idsByNameResults *)
  }

  private[this] val byAnnValSql = SQL("""
    |SELECT zba.trace_id, s.created_ts
    |FROM zipkin_binary_annotations AS zba
    |LEFT JOIN zipkin_spans AS s
    |  ON zba.trace_id = s.trace_id
    |WHERE zba.service_name = {service_name}
    |  AND zba.annotation_key = {annotation}
    |  AND zba.annotation_value = {value}
    |  AND s.created_ts < {end_ts}
    |  AND s.created_ts IS NOT NULL
    |GROUP BY zba.trace_id
    |ORDER BY s.created_ts DESC
    |LIMIT {limit}
  """.stripMargin)

  private[this] val byAnnValResult = (
    long("trace_id") ~
    long("created_ts")
  ) map { case a~b => IndexedTraceId(a, b) }

  private[this] val byAnnSql = SQL("""
    |SELECT trace_id, MAX(a_timestamp)
    |FROM zipkin_annotations
    |WHERE service_name = {service_name}
    |  AND value = {annotation}
    |  AND a_timestamp < {end_ts}
    |GROUP BY trace_id
    |ORDER BY a_timestamp DESC
    |LIMIT {limit}
  """.stripMargin)

  private[this] val byAnnResult = (
    long("trace_id") ~
    long("MAX(a_timestamp)")
  ) map { case a~b => IndexedTraceId(a, b) }

  def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] =
    if (Constants.CoreAnnotations.contains(annotation))
      Future.value(Seq.empty)
    else pool {
      Logger.get.info("zzz SQL: " + byAnnValSql)
      val sql = value
        .map(_ => byAnnValSql)
        .getOrElse(byAnnSql)
        .on("service_name" -> serviceName)
        .on("annotation" -> annotation)
        .on("end_ts" -> endTs)
        .on("limit" -> limit)

      value match {
        case Some(bytes) =>
          sql.on("value" -> Util.getArrayFromBuffer(bytes)).as(byAnnValResult *)
        case None =>
          sql.as(byAnnResult *)
      }
    }

  private[this] def byDurationSql(ids: Seq[Long]) = SQL("""
    |SELECT trace_id, duration, created_ts
    |FROM zipkin_spans
    |WHERE trace_id IN (%s) AND created_ts IS NOT NULL
    |GROUP BY trace_id
  """.stripMargin.format(ids.mkString(",")))

  private[this] val byDurationResults = (
    long("trace_id") ~
    get[Option[Long]]("duration") ~
    long("created_ts")
  ) map { case a~b~c => TraceIdDuration(a, b.getOrElse(0), c) }

  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = pool {
    Logger.get.info("zzz SQL: " + byDurationSql(traceIds))
    byDurationSql(traceIds)
      .as(byDurationResults *)
  }

  private[this] val svcNamesSql = SQL("""
    |SELECT service_name
    |FROM zipkin_annotations
    |GROUP BY service_name
    |ORDER BY service_name ASC
  """.stripMargin)

  def getAllServiceNames: Future[Set[String]] = pool {
    Logger.get.info("zzz SQL: " + svcNamesSql)
    svcNamesSql.as(str("service_name") *).toSet
  }

  private[this] val spanNamesSql = SQL("""
    |SELECT span_name
    |FROM zipkin_annotations
    |WHERE service_name = {service} AND span_name <> ''
    |GROUP BY span_name
    |ORDER BY span_name ASC
  """.stripMargin)

  def getSpanNames(service: String): Future[Set[String]] = pool {
    Logger.get.info("zzz SQL: " + spanNamesSql)
    spanNamesSql.on("service" -> service).as(str("span_name") *).toSet
  }
}
