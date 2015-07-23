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

import java.nio.ByteBuffer

import com.twitter.util.Await
import com.twitter.zipkin.common._
import com.twitter.zipkin.query.Trace
import org.scalatest.FunSuite

class AnormStorageTest extends FunSuite {

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
  val ann4 = Annotation(3, "ss", None)

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(667, "methodcall2", spanId, None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span3 = Span(667, "methodcall3", spanId, None, List(ann4), List(binaryAnnotation("KEY", "VALUE")))

  val span2and3 = span2 mergeSpan span3

  test("tracesExist") {
    /*
     * Database names are irrelevant for the SQLite-memory engine, but for
     * all other engines we want the tests to be isolated.
     */
    val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest1")))
    val con = db.install()
    val storage = new AnormStorage(db, Some(con))

    Await.result(storage.storeSpan(span1))
    Await.result(storage.storeSpan(span2))

    val exist = Await.result(storage.tracesExist(List(span1.traceId, span2.traceId, traceIdDNE)))
    assert(exist === Set(span1.traceId, span2.traceId))
    assert(Await.result(storage.tracesExist(List(span2.traceId))) === Set(span2.traceId))

    con.close()
  }

  test("getSpansByTraceId") {
    val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest2")))
    val con = db.install()
    val storage = new AnormStorage(db, Some(con))

    Await.result(storage.storeSpan(span1))
    Await.result(storage.storeSpan(span2))

    val spans = Await.result(storage.getSpansByTraceId(span1.traceId))
    assert(!spans.isEmpty)
    assert(spans(0) === span1)
    assert(spans.size === 1)

    con.close()
  }

  test("getSpansByTraceIds") {
    val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinStorageTest3")))
    val con = db.install()
    val storage = new AnormStorage(db, Some(con))

    Await.result(storage.storeSpan(span1))
    Await.result(storage.storeSpan(span2))
    Await.result(storage.storeSpan(span3))

    val emptySpans = Await.result(storage.getSpansByTraceIds(List(traceIdDNE)))
    assert(emptySpans.isEmpty)

    val oneSpan = Await.result(storage.getSpansByTraceIds(List(span1.traceId)))
    assert(!oneSpan.isEmpty)
    val trace1 = Trace(oneSpan(0))
    assert(!trace1.spans.isEmpty)
    assert(trace1.spans(0) === span1)

    val twoSpans = Await.result(storage.getSpansByTraceIds(List(span1.traceId, span2.traceId)))
    assert(!twoSpans.isEmpty)
    val trace2a = Trace(twoSpans(0))
    val trace2b = Trace(twoSpans(1))
    assert(!trace2a.spans.isEmpty)
    assert(trace2a.spans(0) === span1)
    assert(!trace2b.spans.isEmpty)
    assert(trace2b.spans.length === 1)

    assert(trace2b.spans(0) === span2and3)

    con.close()
  }
}
