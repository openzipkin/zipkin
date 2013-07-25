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

import org.specs.Specification
import com.twitter.zipkin.common._
import java.nio.ByteBuffer
import com.twitter.util.Await
import com.twitter.zipkin.query.Trace

class AnormStorageSpec extends Specification {

  /*
   * We should be able to switch out the database type and have all the tests
   * still work. However, for most database engines we would need to explicitly
   * create the databases first, or at least mock them.
   */
  val dbType = "sqlite-memory"

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, Some(ep))

  val ep = Endpoint(123, 123, "service")

  val spanId = 456
  val traceIdDNE = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(2, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(667, "methodcall2", spanId, None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))

  "AnormStorage" should {
    "tracesExist" in {
      /*
       * Database names are irrelevant for the SQLite-memory engine, but for
       * all other engines we want the tests to be isolated.
       */
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest1")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))

      Await.result(storage.storeSpan(span1))
      Await.result(storage.storeSpan(span2))

      Await.result(storage.tracesExist(List(span1.traceId, span2.traceId, traceIdDNE))) must haveTheSameElementsAs(Set(span1.traceId, span2.traceId))
      Await.result(storage.tracesExist(List(span2.traceId))) must haveTheSameElementsAs(Set(span2.traceId))
      Await.result(storage.tracesExist(List(traceIdDNE))).isEmpty mustEqual true

      con.close()
    }

    "getSpansByTraceId" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest2")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))

      Await.result(storage.storeSpan(span1))
      Await.result(storage.storeSpan(span2))

      val spans = Await.result(storage.getSpansByTraceId(span1.traceId))
      spans.isEmpty mustEqual false
      spans(0) mustEqual span1
      spans.size mustEqual 1

      con.close()
    }

    "getSpansByTraceIds" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest3")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))

      Await.result(storage.storeSpan(span1))
      Await.result(storage.storeSpan(span2))

      val emptySpans = Await.result(storage.getSpansByTraceIds(List(traceIdDNE)))
      emptySpans.isEmpty mustEqual true

      val oneSpan = Await.result(storage.getSpansByTraceIds(List(span1.traceId)))
      oneSpan.isEmpty mustEqual false
      val trace1 = Trace(oneSpan(0))
      trace1.spans.isEmpty mustEqual false
      trace1.spans(0) mustEqual span1

      val twoSpans = Await.result(storage.getSpansByTraceIds(List(span1.traceId, span2.traceId)))
      twoSpans.isEmpty mustEqual false
      val trace2a = Trace(twoSpans(0))
      val trace2b = Trace(twoSpans(1))
      trace2a.spans.isEmpty mustEqual false
      trace2a.spans(0) mustEqual span1
      trace2b.spans.isEmpty mustEqual false
      trace2b.spans(0) mustEqual span2

      con.close()
    }
  }
}
