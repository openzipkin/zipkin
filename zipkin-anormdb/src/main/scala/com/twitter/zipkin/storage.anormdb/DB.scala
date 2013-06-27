package com.twitter.zipkin.storage.anormdb

//import anorm._
//import anorm.SqlParser._
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
   * Tell JDBC where to find the database.
   *
   * SQLite in-memory:  "jdbc:sqlite::memory:"
   * SQLite persistent: "jdbc:sqlite:DB_NAME"
   * H2 in-memory:      "jdbc:h2:mem:DB_NAME"
   * H2 persistent:     "jdbc:h2:DB_NAME"
   * PostgreSQL:        "jdbc:postgresql://localhost:PORT/DB_NAME"
   * MySQL:             "jdbc:mysql://localhost:PORT/DB_NAME?user=USERNAME&password=PASSWORD"
   */
  private val dbname = "jdbc:sqlite:zipkin.db"

  /**
   * Tell JDBC which database driver to use.
   *
   * SQLite:     "org.sqlite.JDBC"
   * H2:         "org.h2.Driver"
   * PostgreSQL: "org.postgresql.Driver"
   * MySQL:      "com.mysql.jdbc.Driver"
   */
  private val drivername = "org.sqlite.JDBC"

  /**
   * The other place the database driver needs to be set is in the project
   * dependencies in project/Project.scala under the definition of the
   * lazy val anormdb.
   *
   * SQLite:     "org.xerial"     % "sqlite-jdbc"          % "3.7.2"
   * H2:         "com.h2database" % "h2"                   % "1.3.172"
   * PostgreSQL: "postgresql"     % "postgresql"           % "8.4-702.jdbc4" // or "9.1-901.jdbc4"
   * MySQL:      "mysql"          % "mysql-connector-java" % "5.1.25"
   *
   * Also, there's the caveat that some database drivers (notably PostgreSQL)
   * require passing an explicit Properties() object to
   * DriverManager.getConnection() instead of just passing the URL.
   * TODO: Figure out how to deal with that.
   */

  // Load the SQLite driver
  Class.forName(drivername)

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
    DriverManager.getConnection(dbname)
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
}
