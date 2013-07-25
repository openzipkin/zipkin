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

class AnormIndexSpec extends Specification {

  /*
   * We should be able to switch out the database type and have all the tests
   * still work. However, for most database engines we would need to explicitly
   * create the databases first, or at least mock them.
   */
  val dbType = "sqlite-memory"

  val ep = Endpoint(123, 123, "service")

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, Some(ep))

  val spanId = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(2, "custom", Some(ep))
  val ann4 = Annotation(2, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(123, "methodcall", spanId, None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span3 = Span(123, "methodcall", spanId, None, List(ann2, ann3, ann4),
    List(binaryAnnotation("BAH2", "BEH2")))

  val spanEmptySpanName = Span(123, "", spanId, None, List(ann1, ann2), List())
  val spanEmptyServiceName = Span(123, "spanname", spanId, None, List(), List())

  val mergedSpan = Span(123, "methodcall", spanId, None,
    List(ann1, ann2), List(binaryAnnotation("BAH2", "BEH2")))

  "AnormIndex" should {
    "getTraceIdsByName" in {
      /*
       * Database names are irrelevant for the SQLite-memory engine, but for
       * all other engines we want the tests to be isolated.
       */
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinIndexTest1")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))
      val index = new AnormIndex(db, Some(con))

      storage.storeSpan(span1)
      val traces = Await.result(index.getTraceIdsByName("service", None, 3, 3))
      traces foreach {
        _.traceId mustEqual span1.traceId
      }
      traces.isEmpty mustBe false
      val tracesWithSpanName = Await.result(index.getTraceIdsByName("service", Some("methodcall"), 3, 3))
      tracesWithSpanName foreach {
        _.traceId mustEqual span1.traceId
      }
      tracesWithSpanName.isEmpty mustBe false

      con.close()
    }

    "getTraceIdsByAnnotation" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinIndexTest2")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))
      val index = new AnormIndex(db, Some(con))

      storage.storeSpan(span1)

      val normalTraces = Await.result(index.getTraceIdsByAnnotation("service", "custom", None, 3, 3))
      normalTraces.foreach {
        _.traceId mustEqual span1.traceId
      }
      normalTraces.isEmpty mustBe false

      Await.result(index.getTraceIdsByAnnotation("service", "cs", None, 3, 3)).isEmpty mustBe true

      val result = Await.result(index.getTraceIdsByAnnotation("service", "BAH",
        Some(ByteBuffer.wrap("BEH".getBytes)), 3, 3))
      result.foreach {
        _.traceId mustEqual span1.traceId
      }
      result.isEmpty mustBe false

      con.close()
    }

    "getTracesDuration" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinIndexTest3")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))
      val index = new AnormIndex(db, Some(con))

      storage.storeSpan(spanEmptyServiceName)
      Await.result(index.getTracesDuration(Seq(spanEmptyServiceName.traceId))).isEmpty mustEqual true

      storage.storeSpan(span1)
      val duration = Await.result(index.getTracesDuration(Seq(span1.traceId)))
      duration(0).traceId mustEqual span1.traceId
      duration(0).duration mustEqual span1.duration.getOrElse(-1)

      con.close()
    }

    "getServiceNames" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinIndexTest4")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))
      val index = new AnormIndex(db, Some(con))

      storage.storeSpan(spanEmptyServiceName)
      Await.result(index.getServiceNames).isEmpty mustBe true

      storage.storeSpan(span1)
      val serviceNames = Await.result(index.getServiceNames)
      val expectedServices = span1.annotations.map(_.serviceName).toSet
      serviceNames mustEqual expectedServices

      con.close()
    }

    "getSpanNames" in {
      val db = new DB(new DBConfig(dbType, new DBParams(dbName = "zipkinIndexTest5")))
      val con = db.install()
      val storage = new AnormStorage(db, Some(con))
      val index = new AnormIndex(db, Some(con))

      storage.storeSpan(spanEmptySpanName)
      val noSpanNames = Await.result(index.getSpanNames(ann3.serviceName))
      noSpanNames.isEmpty mustBe true

      storage.storeSpan(span1)
      storage.storeSpan(span3)
      val spanNames = Await.result(index.getSpanNames(ann3.serviceName))
      val expectedSpans = Set(span1.name, span3.name)
      spanNames mustEqual expectedSpans

      con.close()
    }
  }
}
