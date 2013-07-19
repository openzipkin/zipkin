package com.twitter.zipkin.storage.anormdb

/*
 * Copyright 2013 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import org.specs._
import anorm._
import anorm.SqlParser._

class AnormDBSpec extends Specification {
  val db = new DB(new DBConfig("sqlite-memory"))
  db.install()

  "AnormDB" should {
    "have the correct schema" in {
      db.withConnection(implicit con => {
        // The right tables are present
        val tables: List[String] = SQL(
          "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
        ).as(str("name") *)
        tables mustEqual List("zipkin_spans", "zipkin_annotations", "zipkin_binary_annotations")

        // The right columns are present
        val cols_spans: List[(Int, String, String, Int, Option[String], Int)] =
          SQL("PRAGMA table_info(zipkin)").as((
            int("cid") ~ str("name") ~ str("type") ~ int("notnull") ~
              get[Option[String]]("dflt_value") ~ int("pk") map flatten) *)
        cols_spans.map(col => (col._2, col._3, col._4)) mustEqual List(
          ("span_id", "BIGINT", 1),
          ("parent_id", "BIGINT", 0),
          ("trace_id", "BIGINT", 1),
          ("span_name", "VARCHAR(255)", 1),
          ("debug", "BOOLEAN", 1),
          ("duration", "BIGINT", 0),
          ("created_ts", "BIGINT", 1)
        )
      })
    }

    "insert and get rows" in {
      db.withConnection(implicit con => {
        // Insert
        val numRowsInserted: Int = SQL(
          "INSERT INTO zipkin_spans VALUES (2, 1, 1, 'mySpan', 1, 1000, 0)"
        ).executeUpdate()
        numRowsInserted mustEqual 1

        // Get
        val result: List[(Long, Option[Long], Long, String, Boolean, Option[Long], Long)] =
          SQL("SELECT * FROM zipkin_spans").as((
            long("span_id") ~ long("parent_id") ~ long("trace_id") ~
              str("span_name") ~ bool("debug") ~ get[Option[Long]]("duration") ~
              long("created_ts") map flatten) *)
        result mustEqual List((2L, Some(1L), 1L, "mySpan", true, Some(1000L), 0L))
      })
    }
  }
}
