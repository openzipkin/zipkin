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

import anorm._
import anorm.SqlParser._
import java.sql.{Blob, Connection, DriverManager}

/**
 * The companion object is mainly to make installation easier.
 */
object DB {
  def apply() = {
    new DB()
  }
}
/**
 * Provides SQL database access via ANORM from the Play framework.
 *
 * See http://www.playframework.com/documentation/2.1.1/ScalaAnorm for
 * documentation on using ANORM.
 */
case class DB(dbconfig: DBConfig = new DBConfig()) {

  // Load the driver
  Class.forName(dbconfig.driver)

  /**
   * Gets a java.sql.Connection to the SQL database.
   *
   * Example usage:
   *
   * implicit val conn: Connection = DB.getConnection()
   * // Do database updates
   * conn.close()
   *
   * This is a useful method if you need to hold a connection open for several
   * transactions that can happen at different times, e.g. in another class
   * that interacts with the database in different methods. If you just need a
   * connection open for a single block of code, see withConnection().
   */
  def getConnection() = {
    DriverManager.getConnection(dbconfig.location)
  }

  /**
   * Execute transactions on a SQL database.
   *
   * A database connection is created when this method is invoked and
   * automatically destroyed when it is completed.
   *
   * Example usage:
   *
   * DB.withConnection { implicit conn =>
   *   // Do database updates
   * }
   *
   * This is useful when you only need a connection open for a single block of
   * code. Because it creates a new connection, if you need to make a lot of
   * updates at different times or locations, it may be better to hold open a
   * single connection using getConnection() as long as you remember to
   * manually close it.
   */
  def withConnection[A](block: Connection => A) : A = {
    val conn = this.getConnection()
    try {
      block(conn)
    }
    finally {
      conn.close()
    }
  }

  /**
   * Set up the database tables.
   *
   * Ideally this should only be run once.
   *
   * Takes a boolean indicating whether to keep the connection open after the
   * installation completes. Pass true for in-memory databases or they will
   * disappear immediately.
   */
  def install(keepAlive: Boolean = false): Connection = {
    implicit val con = this.getConnection()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_spans (
        |  span_id BIGINT NOT NULL,
        |  parent_id BIGINT,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  debug SMALLINT NOT NULL,
        |  duration BIGINT,
        |  created_ts BIGINT NOT NULL
        |)
      """.stripMargin).execute()
    //SQL("CREATE INDEX trace_id ON zipkin_spans (trace_id)").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_annotations (
        |  span_id BIGINT NOT NULL,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  service_name VARCHAR(255) NOT NULL,
        |  value TEXT,
        |  ipv4 INT,
        |  port INT,
        |  timestamp BIGINT NOT NULL,
        |  duration BIGINT
        |)
      """.stripMargin).execute()
    //SQL("CREATE INDEX trace_id ON zipkin_annotations (trace_id)").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_binary_annotations (
        |  span_id BIGINT NOT NULL,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  service_name VARCHAR(255) NOT NULL,
        |  key VARCHAR(255) NOT NULL,
        |  value %s,
        |  annotation_type_value INT NOT NULL,
        |  ipv4 INT,
        |  port INT
        |)
      """.stripMargin.format(this.getBlobType)).execute()
    //SQL("CREATE INDEX trace_id ON zipkin_binary_annotations (trace_id)").execute()
    if (!keepAlive) con.close() else ()
    con
  }

  // Get the column the current database type uses for BLOBs.
  private def getBlobType = dbconfig.description match {
    case "PostgreSQL" => "BYTEA" /* As usual PostgreSQL has to be different */
    case "MySQL" => "MEDIUMBLOB" /* MySQL has length limits, in this case 16MB */
    case _ => "BLOB"
  }

  // Provide Anorm with the ability to handle BLOBs.
  // The documentation says it can do it in 2.1.1, but it's wrong.

  /**
   * Attempt to convert a SQL value into a byte array.
   */
  private def valueToByteArrayOption(value: Any): Option[Array[Byte]] = {
    try {
      value match {
        case bytes: Array[Byte] => Some(bytes)
        case blob: Blob => Some(blob.getBytes(1, blob.length.asInstanceOf[Int]))
        case _ => None
      }
    }
    catch {
      case e: Exception => None
    }
  }

  /**
   * Implicitly convert an ANORM row to a byte array.
   */
  implicit def rowToByteArray: Column[Array[Byte]] = {
    Column.nonNull[Array[Byte]] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      valueToByteArrayOption(value) match {
        case Some(bytes) => Right(bytes)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Byte Array for column " + qualified))
      }
    }
  }

  /**
   * Build a RowParser factory for a byte array column.
   */
  def bytes(columnName: String): RowParser[Array[Byte]] = {
    get[Array[Byte]](columnName)(implicitly[Column[Array[Byte]]])
  }
}
