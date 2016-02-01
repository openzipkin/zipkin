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
      SQL(
        """SELECT parent.endpoint_service_name parent_name, child.endpoint_service_name child_name, COUNT(DISTINCT span.id) count
          |FROM zipkin_spans       span
          |JOIN zipkin_annotations parent ON parent.span_id = span.id
          |JOIN zipkin_annotations child  ON child.span_id  = parent.span_id
          |WHERE start_ts BETWEEN {startTs} AND {endTs}
          |AND parent.a_key IN ("cs","ca")
          |AND child.a_key  IN ("sr","sa")
          |AND parent.endpoint_service_name IS NOT NULL
          |AND child.endpoint_service_name IS NOT NULL
          |GROUP BY parent_name, child_name
        """.stripMargin)
      .on("startTs" -> startTs * 1000)
      .on("endTs" -> endTs * 1000)
      .as((str("parent_name") ~ str("child_name") ~ long("count") map {
        case a~b~c => DependencyLink(a, b, c)
      }) *)
    } finally {
      returnConn(conn, borrowTime, "getDependencies")
    }
  }

  override def storeDependencies(dependencies: Dependencies): Future[Unit] = Future.Unit
}
