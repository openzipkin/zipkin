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

import java.nio.ByteBuffer

import com.twitter.util.Await
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.InMemorySpanStore
import com.twitter.zipkin.thriftscala
import org.scalatest.FunSuite

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
      Await.result(svc.getTraceIdsBySpanName(null, "span", 100, 100, thriftscala.Order.DurationDesc))
    }

    val actual = Await.result(svc.getTraceIdsBySpanName("service2", "methodcall", 1000, 50, thriftscala.Order.DurationDesc))
    assert(actual === Seq(2, 2))
  }

  test("trace summary for trace id") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceSummariesByIds(List(1), List()))
    assert(actual === List(TraceSummary(
      1,
      100,
      150,
      50,
      List(SpanTimestamp("service1", 100, 150)),
      List(ep1)
    ).toThrift))
  }

  test("trace combo for trace id") {
    val svc = newLoadedService()

    val trace = trace1.toThrift
    val summary = TraceSummary(
      1,
      100,
      150,
      50,
      List(SpanTimestamp("service1", 100, 150)),
      List(ep1)
    ).toThrift
    val combo = thriftscala.TraceCombo(trace, Some(summary), Some(Map(666L -> 1)))

    val actual = Await.result(svc.getTraceCombosByIds(List(1), List()))
    assert(actual === Seq(combo))
  }

  test("find trace ids by service name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByServiceName("service3", 1000, 50, thriftscala.Order.DurationDesc))
    assert(actual === Seq(3, 5))
  }

  test("find trace ids by annotation name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByAnnotation("service3", "annotation", null, 1000, 50, thriftscala.Order.DurationDesc))
    assert(actual === Seq(5))
  }

  test("find trace ids by annotation name and value") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByAnnotation("service3", "annotation", ByteBuffer.wrap("ann".getBytes), 1000, 50, thriftscala.Order.DurationDesc))
    assert(actual === Seq(5))
  }

  test("get trace by traceId") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTracesByIds(List(1L), List()))
    assert(actual === List(trace1.toThrift))
  }

  test("get span names") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getSpanNames("service3"))
    assert(actual === Set("methodcall", "otherMethod"))
  }
}
