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
import java.sql.{Connection, SQLRecoverableException, SQLNonTransientException}

class DBSpec extends Specification {

  "DB" should {
    "retry if SQLRecoverableException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.withRecoverableRetry({
        attempt += 1
        throwsRecoverableUnlessSuccess(attempt == 2)
      }).apply() mustEqual true
      attempt mustEqual 2
    }

    "retry only once if SQLRecoverableException is thrown again" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.withRecoverableRetry({
        attempt += 1
        throwsRecoverable
      }).apply() must throwA[SQLRecoverableException]
      attempt mustEqual 2
    }

    "not retry if unrecoverable SQLException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.withRecoverableRetry({
        attempt += 1
        throwsUnrecoverable
      }).apply() must throwA[SQLNonTransientException]
      attempt mustEqual 1
    }

    "[withTransaction] retry if SQLRecoverableException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      implicit val conn: Connection = db.getConnection()
      var attempt = 0
      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        attempt += 1
        throwsRecoverableUnlessSuccess(attempt == 2)
      }).apply() mustEqual true
      attempt mustEqual 2
      conn.close()
    }

    "[withTransaction] retry only once if SQLRecoverableException is thrown again" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      implicit val conn: Connection = db.getConnection()
      var attempt = 0
      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        attempt += 1
        throwsRecoverable
      }).apply() must throwA[SQLRecoverableException]
      attempt mustEqual 2
      conn.close()
    }

    "[withTransaction] not retry if unrecoverable SQLException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      implicit val conn: Connection = db.getConnection()
      var attempt = 0
      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        attempt += 1
        throwsUnrecoverable
      }).apply() must throwA[SQLNonTransientException]
      attempt mustEqual 1
      conn.close()
    }

    "[inNewThread] retry if SQLRecoverableException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.inNewThreadWithRecoverableRetry({
        attempt += 1
        throwsRecoverableUnlessSuccess(attempt == 2)
      }).apply() mustEqual true
      attempt mustEqual 2
    }

    "[inNewThread] retry only once if SQLRecoverableException is thrown again" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.inNewThreadWithRecoverableRetry({
        attempt += 1
        throwsRecoverable
      }).apply() must throwA[SQLRecoverableException]
      attempt mustEqual 2
    }

    "[inNewThread] not retry if unrecoverable SQLException is thrown" in {
      val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
      var attempt = 0
      db.inNewThreadWithRecoverableRetry({
        attempt += 1
        throwsUnrecoverable
      }).apply() must throwA[SQLNonTransientException]
      attempt mustEqual 1
    }

    def throwsRecoverableUnlessSuccess(success: Boolean) : Boolean = {
      if (!success) {
         throwsRecoverable
      }
      success
    }

    def throwsRecoverable() : Boolean = {
      throw new SQLRecoverableException()
    }

    def throwsUnrecoverable() : Boolean = {
      throw new SQLNonTransientException()
    }
  }
}
