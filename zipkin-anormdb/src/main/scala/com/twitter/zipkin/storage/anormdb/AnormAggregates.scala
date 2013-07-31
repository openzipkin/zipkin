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

import com.twitter.util.{FuturePool, Future, Time}
import com.twitter.conversions.time._
import com.twitter.zipkin.common.{Service, DependencyLink, Dependencies}
import com.twitter.zipkin.storage.Aggregates
import java.sql.Connection
import anorm._
import anorm.SqlParser._
import com.twitter.algebird.Moments
import java.util.concurrent.Executors

/**
 * Retrieve and store aggregate dependency information.
 *
 * The top annotations methods are stubbed because they're not currently
 * used anywhere; that feature was never completed.
 */
case class AnormAggregates(db: DB, openCon: Option[Connection] = None) extends Aggregates {
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
   * Close the index
   */
  def close() { conn.close() }

  /**
   * Get the dependencies in a time range.
   *
   * endDate is optional and if not passed defaults to startDate plus one day.
   */
  def getDependencies(startDate: Option[Time], endDate: Option[Time]=None): Future[Dependencies] = sqlFuturePool {
    val startMs = startDate.getOrElse(Time.now - 1.day).inMicroseconds
    val endMs = endDate.getOrElse(Time.now).inMicroseconds

    val links: List[DependencyLink] = SQL(
      """SELECT parent, child, m0, m1, m2, m3, m4
        |FROM zipkin_dependency_links AS l
        |LEFT JOIN zipkin_dependencies AS d
        |  ON l.dlid = d.dlid
        |WHERE start_ts >= {startTs}
        |  AND end_ts <= {endTs}
        |ORDER BY l.dlid DESC
      """.stripMargin)
    .on("startTs" -> startMs)
    .on("endTs" -> endMs)
    .as((str("parent") ~ str("child") ~ long("m0") ~ get[Double]("m1") ~ get[Double]("m2") ~ get[Double]("m3") ~ get[Double]("m4") map {
      case parent ~ child ~ m0 ~ m1 ~ m2 ~ m3 ~ m4 => new DependencyLink(
        new Service(parent),
        new Service(child),
        new Moments(m0, m1, m2, m3, m4)
      )
    }) *)

    new Dependencies(Time.fromNanoseconds(startMs*1000), Time.fromNanoseconds(endMs*1000), links)
  }

  /**
   * Write dependencies
   *
   * Synchronize these so we don't do concurrent writes from the same box
   */
  def storeDependencies(dependencies: Dependencies): Future[Unit] = sqlFuturePool {
    db.withTransaction(conn, { implicit conn: Connection =>
      val dlid = SQL("""INSERT INTO zipkin_dependencies
            |  (start_ts, end_ts)
            |VALUES ({startTs}, {endTs})
          """.stripMargin)
        .on("startTs" -> dependencies.startTime.inMicroseconds)
        .on("endTs" -> dependencies.endTime.inMicroseconds)
      .executeInsert()

      dependencies.links.foreach { link =>
        SQL("""INSERT INTO zipkin_dependency_links
              |  (dlid, parent, child, m0, m1, m2, m3, m4)
              |VALUES ({dlid}, {parent}, {child}, {m0}, {m1}, {m2}, {m3}, {m4})
            """.stripMargin)
          .on("dlid" -> dlid)
          .on("parent" -> link.parent.name)
          .on("child" -> link.child.name)
          .on("m0" -> link.durationMoments.m0)
          .on("m1" -> link.durationMoments.m1)
          .on("m2" -> link.durationMoments.m2)
          .on("m3" -> link.durationMoments.m3)
          .on("m4" -> link.durationMoments.m4)
        .execute()
      }
    })
  }

  /**
   * Get the top annotations for a service name
   */
  def getTopAnnotations(serviceName: String): Future[Seq[String]] = {
    Future.value(Seq.empty[String])
  }

  /**
   * Get the top key value annotation keys for a service name
   */
  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]] = {
    Future.value(Seq.empty[String])
  }

  /**
   * Override the top annotations for a service
   */
  def storeTopAnnotations(serviceName: String, a: Seq[String]): Future[Unit] = {
    Future.Unit
  }

  /**
   * Override the top key value annotation keys for a service
   */
  def storeTopKeyValueAnnotations(serviceName: String, a: Seq[String]): Future[Unit] = {
    Future.Unit
  }
}
