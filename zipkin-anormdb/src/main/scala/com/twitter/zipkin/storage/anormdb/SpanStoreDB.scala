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

import anorm._
import anorm.SqlParser._
import java.sql.{Blob, Connection, DriverManager, SQLException}

case class SpanStoreDB(location: String) {
  val (driver, blobType, autoIncrementSql) = location.split(":").toList match {
    case "sqlite" :: _ =>
      ("org.sqlite.JDBC", "BLOB", "INTEGER PRIMARY KEY AUTOINCREMENT")

    case "h2" :: _ =>
      ("org.h2.Driver", "BLOB", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")

    case "postgresql" :: _ =>
      ("org.postgresql.Driver", "BYTEA", "BIGSERIAL PRIMARY KEY")

    case "mysql" :: _ =>
      ("com.mysql.jdbc.Driver", "MEDIUMBLOB", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")

    case db :: _ =>
      throw new IllegalArgumentException("Unsupported DB: %s".format(db))

    case _ =>
      throw new IllegalArgumentException("Unknown DB location: %s".format(location))
  }

  // Load the driver
  Class.forName(driver)

  /**
   * Gets a java.sql.Connection to the SQL database.
   *
   * Example usage:
   *
   * implicit val conn: Connection = (new SpanStoreDB()).getConnection()
   * // Do database updates
   * conn.close()
   */
  def getConnection() = {
    DriverManager.getConnection("jdbc:" + location)
  }

  /**
   * Attempt to convert a SQL value into a byte array.
   */
  protected def valueToByteArrayOption(value: Any): Option[Array[Byte]] = {
    value match {
      case bytes: Array[Byte] => Some(bytes)
      case blob: Blob =>
        try Some(blob.getBytes(1, blob.length.asInstanceOf[Int])) catch {
          case e: SQLException => None
        }
      case _ => None
    }
  }

  /**
   * Implicitly convert an Anorm row to a byte array.
   */
  def rowToByteArray: Column[Array[Byte]] = {
    Column.nonNull[Array[Byte]] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      valueToByteArrayOption(value) match {
        case Some(bytes) => Right(bytes)
        case _ => Left(TypeDoesNotMatch(
          "Cannot convert %s:%s to Byte Array for column %s".format(
            value, value.asInstanceOf[AnyRef].getClass, qualified)))
      }
    }
  }

  /**
   * Build a RowParser factory for a byte array column.
   */
  def bytes(columnName: String): RowParser[Array[Byte]] = {
    get[Array[Byte]](columnName)(rowToByteArray)
  }

  /**
   * Set up the database tables.
   *
   * Returns an open database connection, so remember to close it, for example
   * with `(new SpanStoreDB()).install().close()`
   */
  def install(clear: Boolean = false): Connection = {
    implicit val con = this.getConnection()
    if (clear) SQL("DROP TABLE IF EXISTS zipkin_spans").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_spans (
        |  span_id BIGINT NOT NULL,
        |  parent_id BIGINT,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  debug SMALLINT NOT NULL,
        |  duration BIGINT,
        |  created_ts BIGINT
        |)
      """.stripMargin).execute()

    if (clear) SQL("DROP TABLE IF EXISTS zipkin_annotations").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_annotations (
        |  span_id BIGINT NOT NULL,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  service_name VARCHAR(255) NOT NULL,
        |  value TEXT,
        |  ipv4 INT,
        |  port INT,
        |  a_timestamp BIGINT NOT NULL,
        |  duration BIGINT
        |)
      """.stripMargin).execute()

    if (clear) SQL("DROP TABLE IF EXISTS zipkin_binary_annotations").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_binary_annotations (
        |  span_id BIGINT NOT NULL,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  service_name VARCHAR(255) NOT NULL,
        |  annotation_key VARCHAR(255) NOT NULL,
        |  annotation_value %s,
        |  annotation_type_value INT NOT NULL,
        |  ipv4 INT,
        |  port INT
        |)
      """.stripMargin.format(blobType)).execute()

    if (clear) SQL("DROP TABLE IF EXISTS zipkin_dependencies").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_dependencies (
        |  dlid %s,
        |  start_ts BIGINT NOT NULL,
        |  end_ts BIGINT NOT NULL
        |)
      """.stripMargin.format(autoIncrementSql)).execute()

    if (clear) SQL("DROP TABLE IF EXISTS zipkin_dependency_links").execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_dependency_links (
        |  dlid BIGINT NOT NULL,
        |  parent VARCHAR(255) NOT NULL,
        |  child VARCHAR(255) NOT NULL,
        |  m0 BIGINT NOT NULL,
        |  m1 DOUBLE PRECISION NOT NULL,
        |  m2 DOUBLE PRECISION NOT NULL,
        |  m3 DOUBLE PRECISION NOT NULL,
        |  m4 DOUBLE PRECISION NOT NULL
        |)
      """.stripMargin).execute()
    con
  }
}
