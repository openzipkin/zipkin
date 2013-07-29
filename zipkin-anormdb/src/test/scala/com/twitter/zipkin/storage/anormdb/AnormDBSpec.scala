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

  "AnormDB" should {
    "have the correct schema" in {
      implicit val con = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest"))).install()
      val expectedTables = List("zipkin_annotations", "zipkin_binary_annotations", "zipkin_spans", "zipkin_dependencies", "zipkin_dependency_links")
      // The right tables are present
      val tables: List[String] = SQL(
        "SELECT name FROM sqlite_master WHERE type='table'"
      ).as(str("name") *)
      tables must containAll(expectedTables)

      val expectedCols = List(
        ("span_id", "BIGINT", 1),
        ("parent_id", "BIGINT", 0),
        ("trace_id", "BIGINT", 1),
        ("span_name", "VARCHAR(255)", 1),
        ("debug", "SMALLINT", 1),
        ("duration", "BIGINT", 0),
        ("created_ts", "BIGINT", 0)
      )
      // The right columns are present
      val cols_spans: List[(Int, String, String, Int, Option[String], Int)] =
        SQL("PRAGMA table_info(zipkin_spans)").as((
          int("cid") ~ str("name") ~ str("type") ~ int("notnull") ~
            get[Option[String]]("dflt_value") ~ int("pk") map flatten) *)
      val better_cols = cols_spans.map { col => (col._2, col._3, col._4) }
      better_cols must containAll(expectedCols)
      con.close()
    }

    "insert and get rows" in {
      implicit val con = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest"))).install()
      // Insert
      val numRowsInserted: Int = SQL(
        "INSERT INTO zipkin_spans VALUES (2, 1, 1, 'mySpan', 1, 1000, 0)"
      ).executeUpdate()
      numRowsInserted mustEqual 1

      // Get
      val result: List[(Long, Option[Long], Long, String, Int, Option[Long], Long)] =
        SQL("SELECT * FROM zipkin_spans").as((
          long("span_id") ~ get[Option[Long]]("parent_id") ~ long("trace_id") ~
            str("span_name") ~ int("debug") ~ get[Option[Long]]("duration") ~
            long("created_ts") map flatten) *)
      result mustEqual List((2L, Some(1L), 1L, "mySpan", 1, Some(1000L), 0L))
      con.close()
    }

  }
}
