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

import java.sql.{Connection, SQLNonTransientException, SQLRecoverableException}

import com.twitter.util.Await
import org.scalatest.{FunSuite, Matchers}

class DBSpec extends FunSuite with Matchers {

  test("retry if SQLRecoverableException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    db.withRecoverableRetry({
      attempt += 1
      throwsRecoverableUnlessSuccess(attempt == 2)
    }).apply() should be (true)
    attempt should be (2)
  }

  test("retry only once if SQLRecoverableException is thrown again") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    a [SQLRecoverableException] should be thrownBy {
      db.withRecoverableRetry({
        attempt += 1
        throwsRecoverable
      }).apply()
    }
    attempt should be (2)
  }

  test("not retry if unrecoverable SQLException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    a [SQLNonTransientException] should be thrownBy {
      db.withRecoverableRetry({
        attempt += 1
        throwsUnrecoverable
      }).apply()
    }
    attempt should be (1)
  }

  test("[withTransaction] retry if SQLRecoverableException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    implicit val conn: Connection = db.getConnection()
    var attempt = 0
    db.withRecoverableTransaction(conn, { implicit conn: Connection =>
      attempt += 1
      throwsRecoverableUnlessSuccess(attempt == 2)
    }).apply() should be (true)
    attempt should be (2)
    conn.close()
  }

  test("[withTransaction] retry only once if SQLRecoverableException is thrown again") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    implicit val conn: Connection = db.getConnection()
    var attempt = 0
    a [SQLRecoverableException] should be thrownBy {
      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        attempt += 1
        throwsRecoverable
      }).apply()
    }
    attempt should be (2)
    conn.close()
  }

  test("[withTransaction] not retry if unrecoverable SQLException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    implicit val conn: Connection = db.getConnection()
    var attempt = 0
    a [SQLNonTransientException] should be thrownBy {
      db.withRecoverableTransaction(conn, { implicit conn: Connection =>
        attempt += 1
        throwsUnrecoverable
      }).apply()
    }
    attempt should be (1)
    conn.close()
  }

  test("[inNewThread] retry if SQLRecoverableException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    Await.result(db.inNewThreadWithRecoverableRetry({
      attempt += 1
      throwsRecoverableUnlessSuccess(attempt == 2)
    })) should be (true)
    attempt should be (2)
  }

  test("[inNewThread] retry only once if SQLRecoverableException is thrown again") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    a [SQLRecoverableException] should be thrownBy {
      Await.result(db.inNewThreadWithRecoverableRetry({
        attempt += 1
        throwsRecoverable
      }))
    }
    attempt should be (2)
  }

  test("[inNewThread] not retry if unrecoverable SQLException is thrown") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinTest")))
    var attempt = 0
    a [SQLNonTransientException] should be thrownBy {
      Await.result(db.inNewThreadWithRecoverableRetry({
        attempt += 1
        throwsUnrecoverable
      }))
    }
    attempt should be (1)
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
