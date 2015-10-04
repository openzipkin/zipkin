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

import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.{Future, Time}
import com.twitter.zipkin.common.{Dependencies, DependencyLink}
import com.twitter.zipkin.storage.DependencyStore
import com.twitter.zipkin.storage.anormdb.AnormThreads.inNewThread
import java.sql.Connection
import anorm.SqlParser._
import anorm._
import java.util.concurrent.TimeUnit._

/**
 * Retrieve and store aggregate dependency information.
 *
 * The top annotations methods are stubbed because they're not currently
 * used anywhere; that feature was never completed.
 */
case class AnormDependencyStore(val db: DB,
                                val openCon: Option[Connection] = None,
                                val stats: StatsReceiver = DefaultStatsReceiver.scope("AnormDependencyStore")
                                 ) extends DependencyStore with DBPool {


  case class DependencyInterval(startTs: Long, endTs: Long, startId: Long, endId: Long)

  override def getDependencies(_startTs: Option[Long], _endTs: Option[Long] = None): Future[Dependencies] = db.inNewThreadWithRecoverableRetry {
    val endTs = _endTs.getOrElse(Time.now.inMicroseconds)
    val startTs = _startTs.getOrElse(endTs - MICROSECONDS.convert(1, DAYS))

	implicit val (conn, borrowTime) = borrowConn()
	try {

    SQL(
      """SELECT min(start_ts), max(end_ts), min(dlid), max(dlid)
        |FROM zipkin_dependencies
        |WHERE start_ts >= {startTs}
        |  AND end_ts <= {endTs}
      """.stripMargin)
      .on("startTs" -> startTs)
      .on("endTs" -> endTs)
      .as((long("min(start_ts)").? ~ long("max(end_ts)").? ~ long("min(dlid)").? ~ long("max(dlid)").? map {
      case startTs ~ endTs ~ startId ~ endId => {
        startTs.map(DependencyInterval(_, endTs.get, startId.get, endId.get))
      }
    }) *).flatMap(_.headOption).headOption.map(interval => {

      val links: List[DependencyLink] = SQL(
        """SELECT parent, child, call_count
          |FROM zipkin_dependency_links
          |WHERE dlid >= {startId}
          |  AND dlid <= {endId}
          |ORDER BY dlid DESC
        """.stripMargin)
        .on("startId" -> interval.startId)
        .on("endId" -> interval.endId)
        .as((str("parent") ~ str("child") ~ long("call_count") map {
        case parent ~ child ~ callCount => new DependencyLink(parent,child, callCount)
      }) *)
      Dependencies(interval.startTs, interval.endTs, links)
    }).getOrElse(Dependencies.zero)

    } finally {
      returnConn(conn, borrowTime, "getDependencies")
    }
  }

  /**
   * Write dependencies
   *
   * Synchronize these so we don't do concurrent writes from the same box
   */
  override def storeDependencies(dependencies: Dependencies): Future[Unit] = inNewThread {
	implicit val (conn, borrowTime) = borrowConn()
	try {

    db.withRecoverableTransaction(conn, { implicit conn: Connection =>
      val dlid = SQL("""INSERT INTO zipkin_dependencies
            |  (start_ts, end_ts)
            |VALUES ({startTs}, {endTs})
          """.stripMargin)
        .on("startTs" -> dependencies.startTs)
        .on("endTs" -> dependencies.endTs)
      .executeInsert()

      dependencies.links.foreach { link =>
        SQL("""INSERT INTO zipkin_dependency_links
              |  (dlid, parent, child, call_count)
              |VALUES ({dlid}, {parent}, {child}, {callCount})
            """.stripMargin)
          .on("dlid" -> dlid)
          .on("parent" -> link.parent)
          .on("child" -> link.child)
          .on("callCount" -> link.callCount)
        .execute()
      }
    })

    } finally {
      returnConn(conn, borrowTime, "storeDependencies")
    }
  }
}
