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

import anorm.SqlParser._
import anorm._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.Future
import com.twitter.zipkin.common.{Dependencies, DependencyLink}
import com.twitter.zipkin.storage.DependencyStore
import java.sql.Connection

case class AnormDependencyStore(val db: DB,
                                val openCon: Option[Connection] = None,
                                val stats: StatsReceiver = DefaultStatsReceiver.scope("AnormDependencyStore")
                                 ) extends DependencyStore with DBPool {

  override def getDependencies(endTs: Long, lookback: Option[Long]): Future[Seq[DependencyLink]] = db.inNewThreadWithRecoverableRetry {
    val startTs = endTs - lookback.getOrElse(endTs)

    implicit val (conn, borrowTime) = borrowConn()
    try {
      val parentChild = SQL(
        """SELECT trace_id, parent_id, id
          |FROM zipkin_spans
          |WHERE start_ts BETWEEN {startTs} AND {endTs}
          |AND parent_id is not null
        """.stripMargin)
        .on("startTs" -> startTs * 1000)
        .on("endTs" -> endTs * 1000)
        .as((long("trace_id") ~ long("parent_id") ~ long("id") map {
          case traceId ~ parentId ~ id => (traceId, parentId, id)
        }) *).groupBy(_._1)

      val traceSpanServiceName: Map[(Long, Long), String] = SQL(
        """SELECT DISTINCT trace_id, span_id, endpoint_service_name
          |FROM zipkin_annotations
          |WHERE trace_id IN (%s)
          |AND a_key in ("sr","sa")
          |AND endpoint_service_name is not null
          |GROUP BY trace_id, span_id
        """.stripMargin.format(parentChild.keys.mkString(",")))
        .as((long("trace_id") ~ long("span_id") ~ str("endpoint_service_name") map {
          case traceId ~ spanId ~ serviceName => (traceId, spanId, serviceName)
        }) *).map(r => (r._1, r._2) -> r._3).toMap

      parentChild.values.flatMap(identity).flatMap(r => {
        // parent can be empty if a root span is missing
        for (
          parent <- traceSpanServiceName.get((r._1, r._2));
          child <- traceSpanServiceName.get((r._1, r._3))
        ) yield (parent, child)
      })
      .groupBy(identity).mapValues(_.size) // sum span count
      .map{ case ((parent, child), count) => DependencyLink(parent, child, count)}.toSeq
    } finally {
      returnConn(conn, borrowTime, "getDependencies")
    }
  }

  override def storeDependencies(dependencies: Dependencies): Future[Unit] = Future.Unit
}
