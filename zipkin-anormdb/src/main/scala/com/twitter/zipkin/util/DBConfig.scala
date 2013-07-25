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

/**
 * Database connection parameters.
 *
 * Not all of these matter for all database types. For example, in-memory
 * SQLite doesn't require any of these parameters to connect. The parameters
 * that are used for different databases can be seen in the "location" method
 * of the relevant DBInfo instance in DBConfig below.
 *
 * NOTE: YOUR PASSWORD SHOULD BE IN CONFIGURATION, NOT CODE.
 *
 * @param dbName The name of the database
 * @param host The URL or IP address where the database is located
 * @param port The port on which the database is listening, if not the default
 * @param username The username to log in to the database
 * @param password The password to log in to the database
 * @param ssl Whether to connect using SSL if possible
 */
case class DBParams(
                     dbName: String = "zipkin",
                     host: String = "localhost",
                     port: Option[Int] = None,
                     username: String = "",
                     password: String = "",
                     ssl: Boolean = false) {
  def getPort = if (port.isEmpty) "" else ":" + port.get
}

/**
 * Database connection configuration.
 *
 * @param name The database type. Must match a key of the dbmap property
 * @param params Connection information
 * @param install Whether to set up the database schema.
 *                The schema can be installed multiple times with no problems.
 */
case class DBConfig(name: String = "sqlite-persistent",
                    params: DBParams = new DBParams(),
                    install: Boolean = false) {

  case class DBInfo(driver: String, description: String, location: DBParams => String)

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
   * dependencies in project/Project.scala.
   */
  private val dbmap = Map(
    "sqlite-memory" -> DBInfo(
      description = "SQLite in-memory",
      driver = "org.sqlite.JDBC",
      location = { _ => "jdbc:sqlite::memory:" }
    ),
    "sqlite-persistent" -> DBInfo(
      description = "SQLite persistent",
      driver = "org.sqlite.JDBC",
      location = { dbp: DBParams => "jdbc:sqlite:" + dbp.dbName + ".db" }
    ),
    "h2-memory" -> DBInfo(
      description = "H2 in-memory",
      driver = "org.h2.Driver",
      location = { dbp: DBParams => "jdbc:h2:mem:" + dbp.dbName }
    ),
    "h2-persistent" -> DBInfo(
      description = "H2 persistent",
      driver = "org.h2.Driver",
      location = { dbp: DBParams => "jdbc:h2:" + dbp.dbName }
    ),
    "postgresql" -> DBInfo(
      description = "PostgreSQL",
      driver = "org.postgresql.Driver",
      location = { dbp: DBParams =>
        "jdbc:postgresql://" + dbp.host + dbp.getPort + "/" + dbp.dbName + "?user=" + dbp.username + "&password=" + dbp.password + "&ssl=" + dbp.ssl
      }
    ),
    "mysql" -> DBInfo(
      description = "MySQL",
      driver = "com.mysql.jdbc.Driver",
      location = { dbp: DBParams =>
        "jdbc:mysql://" + dbp.host + dbp.getPort + "/" + dbp.dbName + "?user=" + dbp.username + "&password=" + dbp.password
      }
    )
  )
  private val dbinfo = dbmap(name)

  def description: String = dbinfo.description
  def driver: String = dbinfo.driver
  def location: String = dbinfo.location(params)
}
