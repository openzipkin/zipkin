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

import java.sql.{Blob, Connection, DriverManager, PreparedStatement, SQLException, SQLRecoverableException}

import anorm.SqlParser._
import anorm._
import com.twitter.util.{Future, Return, Throw, Try}
import com.twitter.zipkin.storage.anormdb.AnormThreads.inNewThread
import com.zaxxer.hikari.HikariDataSource

/**
 * Provides SQL database access via Anorm from the Play framework.
 *
 * See http://www.playframework.com/documentation/2.1.1/ScalaAnorm for
 * documentation on using Anorm.
 */
case class DB(dbconfig: DBConfig = new DBConfig()) {

  // Load the driver
  Class.forName(dbconfig.driver)

  // Install the schema if requested
  if (dbconfig.install) this.install().close()

  // Initialize connection pool
  private val connpool = new HikariDataSource()
  connpool.setDriverClassName(dbconfig.driver)
  connpool.setJdbcUrl(dbconfig.location)
  connpool.setConnectionTestQuery(if (dbconfig.jdbc3) "SELECT 1" else null)
  connpool.setMaximumPoolSize(dbconfig.maxConnections)

  /**
   * Gets a dedicated java.sql.Connection to the SQL database. Note that auto-commit is
   * enabled by default.
   *
   * Example usage:
   *
   * implicit val conn: Connection = (new DB()).getConnection()
   * // Do database updates
   * conn.close()
   */
  def getConnection() = {
    val conn = DriverManager.getConnection(dbconfig.location)
    conn.setAutoCommit(true)
    conn
  }

  /**
   * Gets a pooled java.sql.Connection to the SQL database. Note that auto-commit is
   * enabled by default.
   *
   * Example usage:
   *
   * implicit val conn: Connection = db.getPooledConnection()
   * // Do database updates
   * conn.close()
   */
  def getPooledConnection() = {
    val conn = connpool.getConnection()
    conn.setAutoCommit(true)
    conn
  }

  /**
   * Closes the connection pool
   */
  def closeConnectionPool() = {
    connpool.close()
  }

  /**
   * Execute SQL in a transaction.
   *
   * Example usage:
   *
   * db.withTransaction(conn, { implicit conn: Connection =>
   *   // Do database updates
   * })
   */
  def withTransaction[A](conn: Connection, code: Connection => A): Try[A] = {
    val autoCommit = conn.getAutoCommit
    try {
      conn.setAutoCommit(false)
      val result = code(conn)
      conn.commit()
      Return(result)
    }
    catch {
      case e: Throwable => {
        conn.rollback()
        Throw(e)
      }
    }
    finally {
      conn.setAutoCommit(autoCommit)
    }
  }

  /**
   * Performs an automatic retry if an SQLRecoverableException is caught.
   */
  def withRecoverableRetry[A](code: => A): Try[A] = {
    try {
      Return(code)
    }
    catch {
      case e: SQLRecoverableException => {
        Return(code)
      }
    }
  }

  /**
   * Wrapper for withTransaction that performs an automatic retry if an SQLRecoverableException is caught.
   *
   * Example usage:
   *
   * db.withRecoverableTransaction(conn, { implicit conn: Connection =>
   *   // Do database updates
   * })
   */
  def withRecoverableTransaction[A](conn: Connection, code: Connection => A): Try[A] = {
    withRecoverableRetry(withTransaction(conn, code).apply())
  }

  /**
   * Wrapper for inNewThread that performs an automatic retry if an SQLRecoverableException is caught.
   */
  def inNewThreadWithRecoverableRetry[A](code: => A): Future[A] = {
    inNewThread {
      withRecoverableRetry(code).apply()
    }
  }

  /**
   * Set up the database tables.
   *
   * Returns an open database connection, so remember to close it, for example
   * with `(new DB()).install().close()`
   *
   * @throws IllegalArgumentException if "MySQL" is the the database description. Install directly
   *                                  from "zipkin-anormdb/src/main/resources/mysql.sql"
   */
  def install(): Connection = {
    if (dbconfig.description == "MySQL") {
      throw new IllegalArgumentException("Please install MySQL schema directly: zipkin-anormdb/src/main/resources/mysql.sql")
    } else if (!dbconfig.description.startsWith("SQLite")) {
      throw new IllegalArgumentException("Auto-install schema is only supported for SQLite, not: " + dbconfig.description);
    }

    implicit val con = this.getConnection()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_spans (
        |  trace_id BIGINT NOT NULL,
        |  id BIGINT NOT NULL,
        |  name VARCHAR(255) NOT NULL,
        |  parent_id BIGINT,
        |  debug SMALLINT,
        |  start_ts BIGINT,
        |  duration BIGINT
        |)
      """.stripMargin).execute()
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_annotations (
        |  trace_id BIGINT NOT NULL,
        |  span_id BIGINT NOT NULL,
        |  a_key VARCHAR(255) NOT NULL,
        |  a_value BLOB,
        |  a_type INT NOT NULL,
        |  a_timestamp BIGINT NOT NULL,
        |  endpoint_ipv4 INT,
        |  endpoint_port SMALLINT,
        |  endpoint_service_name VARCHAR(255) NOT NULL
        |)
      """.stripMargin).execute()
    con
  }

  /**
   * Get the command to use for inserting span rows.
   */
  def replaceCommand(): String = {
    dbconfig.description match {
      case "MySQL" => "REPLACE" // Prevents primary key conflict errors if duplicates are received
      case _ => "INSERT"
    }
  }

  // (Below) Provide Anorm with the ability to handle BLOBs.
  // The documentation says it can do it in 2.1.1, but it's wrong.

  /**
   * Attempt to convert a SQL value into a byte array.
   */
  private def valueToByteArrayOption(value: Any): Option[Array[Byte]] = {
    value match {
      case bytes: Array[Byte] => Some(bytes)
      case blob: Blob => try {
          Some(blob.getBytes(1, blob.length.asInstanceOf[Int]))
        }
        catch {
          case e: SQLException => None
        }
      case _ => None
    }
  }

  /**
   * Implicitly convert an Anorm row to a byte array.
   */
  def rowToByteArray: Column[Array[Byte]] = {
    Column.nonNull1[Array[Byte]] { (value, meta) =>
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
    get[Array[Byte]](columnName)(rowToByteArray)
  }
}

object DB {
  implicit object byteArrayToStatement extends ToStatement[Array[Byte]] {
    def set(s: PreparedStatement, i: Int, b: Array[Byte]): Unit = s.setBytes(i, b)
  }
}
