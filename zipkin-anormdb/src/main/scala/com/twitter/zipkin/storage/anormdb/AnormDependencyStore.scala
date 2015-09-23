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

import com.twitter.util.{Future, Time}
import com.twitter.conversions.time._
import com.twitter.zipkin.common.{DependencyLink, Dependencies}
import com.twitter.zipkin.storage.DependencyStore
import java.sql.Connection
import anorm._
import anorm.SqlParser._
import AnormThreads.inNewThread

/**
 * Retrieve and store aggregate dependency information.
 *
 * The top annotations methods are stubbed because they're not currently
 * used anywhere; that feature was never completed.
 */
case class AnormDependencyStore(
  val db: DB,
  val openCon: Option[Connection] = None) extends DependencyStore with DBPool {

  /**
   * Get the dependencies in a time range.
   *
   * endDate is optional and if not passed defaults to startDate plus one day.
   */
  def getDependencies(startDate: Option[Time], endDate: Option[Time]=None): Future[Dependencies] = db.inNewThreadWithRecoverableRetry {
    val startMs = startDate.getOrElse(Time.now - 1.day).inMicroseconds
    val endMs = endDate.getOrElse(Time.now).inMicroseconds

	implicit val (conn, borrowTime) = borrowConn()
	try {

    val links: List[DependencyLink] = SQL(
      """SELECT parent, child, call_count
        |FROM zipkin_dependency_links AS l
        |LEFT JOIN zipkin_dependencies AS d
        |  ON l.dlid = d.dlid
        |WHERE start_ts >= {startTs}
        |  AND end_ts <= {endTs}
        |ORDER BY l.dlid DESC
      """.stripMargin)
    .on("startTs" -> startMs)
    .on("endTs" -> endMs)
    .as((str("parent") ~ str("child") ~ long("call_count") map {
      case parent ~ child ~ callCount => new DependencyLink(parent,child, callCount)
    }) *)

    new Dependencies(Time.fromMicroseconds(startMs), Time.fromMicroseconds(endMs), links)

    } finally {
      returnConn(conn, borrowTime, "getDependencies")
    }
  }

  /**
   * Write dependencies
   *
   * Synchronize these so we don't do concurrent writes from the same box
   */
  def storeDependencies(dependencies: Dependencies): Future[Unit] = inNewThread {
	implicit val (conn, borrowTime) = borrowConn()
	try {

    db.withRecoverableTransaction(conn, { implicit conn: Connection =>
      val dlid = SQL("""INSERT INTO zipkin_dependencies
            |  (start_ts, end_ts)
            |VALUES ({startTs}, {endTs})
          """.stripMargin)
        .on("startTs" -> dependencies.startTime.inMicroseconds)
        .on("endTs" -> dependencies.endTime.inMicroseconds)
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
