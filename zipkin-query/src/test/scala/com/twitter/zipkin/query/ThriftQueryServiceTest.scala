/*
 * Copyright 2012 Twitter Inc.
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
package com.twitter.zipkin.query

import com.twitter.util.Await
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.InMemorySpanStore
import com.twitter.zipkin.thriftscala
import org.scalatest.FunSuite
import java.nio.ByteBuffer

class ThriftQueryServiceTest extends FunSuite {
  val ep1 = Endpoint(123, 123, "service1")
  val ep2 = Endpoint(234, 234, "service2")
  val ep3 = Endpoint(345, 345, "service3")
  val ann1 = Annotation(100, thriftscala.Constants.CLIENT_SEND, Some(ep1))
  val ann2 = Annotation(150, thriftscala.Constants.CLIENT_RECV, Some(ep1))
  val spans1 = List(Span(1, "methodcall", 666, None, List(ann1, ann2), Nil))
  val trace1 = Trace(spans1)
  // duration 50

  val ann3 = Annotation(101, thriftscala.Constants.CLIENT_SEND, Some(ep2))
  val ann4 = Annotation(501, thriftscala.Constants.CLIENT_RECV, Some(ep2))
  val spans2 = List(Span(2, "methodcall", 667, None, List(ann3, ann4), Nil))
  val trace2 = Trace(spans2)
  // duration 400

  val ann5 = Annotation(99, thriftscala.Constants.CLIENT_SEND, Some(ep3))
  val ann6 = Annotation(199, thriftscala.Constants.CLIENT_RECV, Some(ep3))
  val spans3 = List(Span(3, "methodcall", 668, None, List(ann5, ann6), Nil))
  val trace3 = Trace(spans3)
  // duration 100

  // get some server action going on
  val ann7 = Annotation(110, thriftscala.Constants.SERVER_RECV, Some(ep2))
  val ann8 = Annotation(140, thriftscala.Constants.SERVER_SEND, Some(ep2))
  val spans4 = List(
    Span(2, "methodcall", 666, None, List(ann1, ann2), Nil),
    Span(2, "methodcall", 666, None, List(ann7, ann8), Nil))
  val trace4 = Trace(spans4)

  val ann9 = Annotation(60, thriftscala.Constants.CLIENT_SEND, Some(ep3))
  val ann10 = Annotation(65, "annotation", Some(ep3))
  val ann11 = Annotation(100, thriftscala.Constants.CLIENT_RECV, Some(ep3))
  val bAnn1 = BinaryAnnotation("annotation", ByteBuffer.wrap("ann".getBytes), AnnotationType.String, Some(ep3))
  val spans5 = List(Span(5, "otherMethod", 666, None, List(ann9, ann10, ann11), List(bAnn1)))
  // duration 40

  val allSpans = spans1 ++ spans2 ++ spans3 ++ spans4 ++ spans5

  // no spans
  val emptyTrace = Trace(List())

  def newLoadedService(spans: Seq[Span] = allSpans): ThriftQueryService = {
    val store = new InMemorySpanStore
    Await.ready(store(spans))
    new ThriftQueryService(store)
  }

  test("find traces by span name") {
    val svc = newLoadedService()

    // exception on null serviceName
    intercept[thriftscala.QueryException] {
      Await.result(svc.getTraces(thriftscala.QueryRequest(null, Some("span"), None, None, 100, 100)))
    }

    val actual = Await.result(svc.getTraces(thriftscala.QueryRequest("service2", Some("methodcall"), None, None, 1000, 50)))
    assert(actual.map(_.spans.head.traceId) === Seq(2, 2))
  }

  test("find traces by service name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraces(thriftscala.QueryRequest("service3", None, None, None, 1000, 50)))
    assert(actual.map(_.spans.head.traceId) === Seq(5, 3)) // Note trace 5's timestamp is before trace 3's
  }

  test("find traces by annotation name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraces(thriftscala.QueryRequest("service3", None, Some(Seq("annotation")), None, 1000, 50)))
    assert(actual.map(_.spans.head.traceId) === Seq(5))
  }

  test("find traces by annotation name and value") {
    val svc = newLoadedService()
    val keyValue = Map("annotation" -> "ann")

    val actual = Await.result(svc.getTraces(thriftscala.QueryRequest("service3", None, None, Some(keyValue), 1000, 50)))
    assert(actual.map(_.spans.head.traceId) === Seq(5))
  }

  test("get traces by traceId") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTracesByIds(List(1L)))
    assert(actual === List(trace1.toThrift))
  }

  test("get span names") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getSpanNames("service3"))
    assert(actual === Set("methodcall", "otherMethod"))
  }
}
