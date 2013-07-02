package com.twitter.zipkin.storage.anormdb

import anorm._
import java.sql.Connection
import java.sql.DriverManager

/**
 * Provides SQL database access via ANORM from the Play framework.
 *
 * See http://www.playframework.com/documentation/2.1.1/ScalaAnorm for
 * documentation on using ANORM.
 */
object DB {
  /**
   * Database information.
   *
   * The key of the outer map and the descriptions are arbitrary. The location
   * is the name of the database and where to find it. The driver is the JDBC
   * interface to the database type.
   *
   * Anorm supports any SQL database, so more databases can be added here.
   *
   * The other place the database driver needs to be set is in the project
   * dependencies in project/Project.scala under the definition of the
   * lazy val anormdb.
   *
   * SQLite:     "org.xerial"     % "sqlite-jdbc"          % "3.7.2"
   * H2:         "com.h2database" % "h2"                   % "1.3.172"
   * PostgreSQL: "postgresql"     % "postgresql"           % "8.4-702.jdbc4" // or "9.1-901.jdbc4"
   * MySQL:      "mysql"          % "mysql-connector-java" % "5.1.25"
   *
   * TODO: Figure out the easiest way for someone to set up the schema the first time, e.g. by running DB.install().
   */
  private val dbmap = Map(
    "sqlite-memory" -> Map(
      "description" -> "SQLite in-memory",
      "location" -> "jdbc:sqlite::memory:",
      "driver" -> "org.sqlite.JDBC"
    ),
    "sqlite-persistent" -> Map(
      "description" -> "SQLite persistent",
      "location" -> "jdbc:sqlite:[DB_NAME].db",
      "driver" -> "org.sqlite.JDBC"
    ),
    "h2-memory" -> Map(
      "description" -> "H2 in-memory",
      "location" -> "jdbc:h2:mem:zipkin",
      "driver" -> "org.h2.Driver"
    ),
    "h2-persistent" -> Map(
      "description" -> "H2 persistent",
      "location" -> "jdbc:h2:[DB_NAME]",
      "driver" -> "org.h2.Driver"
    ),
    "postgresql" -> Map(
      "description" -> "PostgreSQL",
      "location" -> "jdbc:postgresql://[HOST][PORT]/[DB_NAME]?user=[USERNAME]&password=[PASSWORD]&ssl=[SSL]",
      "driver" -> "org.postgresql.Driver"
    ),
    "mysql" -> Map(
      "description" -> "MySQL",
      "location" -> "jdbc:mysql://[HOST][PORT]/[DB_NAME]?user=[USERNAME]&password=[PASSWORD]",
      "driver" -> "com.mysql.jdbc.Driver"
    )
  )

  /**
   * Configuration variables.
   *
   * TODO: The config we want to use should be loaded from configuration.
   */
  private val dbconfig = Map(
    "info" -> Map(
      "type" -> "sqlite-persistent"
    ),
    "params" -> Map(
      "SSL" -> "false",
      "PASSWORD" -> "",
      "USERNAME" -> "",
      "DB_NAME" -> "zipkin",
      "HOST" -> "localhost",
      "PORT" -> ""
    )
  )

  /**
   * The database type we want to use.
   *
   * This must correspond to an outer key of the dbmap Map.
   */
  private val dbinfo = dbmap(dbconfig("info")("type"))

  // Load the driver
  Class.forName(dbinfo("driver"))

  /**
   * Return a description of the database type in use.
   *
   * This can be used for statements that use syntax specific to a certain
   * database.
   */
  def getName() = {
    dbinfo("description")
  }

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
    DriverManager.getConnection(parseLocation())
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
   */
  def install() = this.withConnection { implicit con =>
    SQL(
      """CREATE TABLE IF NOT EXISTS zipkin_spans (
        |  span_id BIGINT NOT NULL,
        |  parent_id BIGINT,
        |  trace_id BIGINT NOT NULL,
        |  span_name VARCHAR(255) NOT NULL,
        |  debug BOOLEAN NOT NULL,
        |  duration BIGINT,
        |  created_ts BIGINT
        |)
      """.stripMargin).execute()
    SQL("CREATE INDEX trace_id ON zipkin_spans (trace_id)").execute()
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
    SQL("CREATE INDEX trace_id ON zipkin_annotations (trace_id)").execute()
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
    SQL("CREATE INDEX trace_id ON zipkin_binary_annotations (trace_id)").execute()
  }

  // Get the column the current database type uses for BLOBs.
  private def getBlobType = this.getName match {
    case "PostgreSQL" => "BYTEA" /* As usual PostgreSQL has to be different */
    case "MySQL" => "MEDIUMBLOB" /* MySQL has length limits, in this case 16MB */
    case _ => "BLOB"
  }

  // Substitute the database configuration into the location string.
  // This allows storing things like the database password in config.
  private def parseLocation():String = {
    var loc = dbinfo("location")
    for ((k:String, v:String) <- dbconfig("params")) {
      if (k == "PORT" && v != "")
        loc = loc.replaceFirst("[" + k + "]", ":" + v)
      else
        loc = loc.replaceFirst("[" + k + "]", v)
    }
    loc
  }
}
