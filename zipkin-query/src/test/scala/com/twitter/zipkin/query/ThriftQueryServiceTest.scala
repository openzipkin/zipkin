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

import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.InMemorySpanStore
import com.twitter.zipkin.{gen => thrift}
import java.nio.ByteBuffer
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ThriftQueryServiceTest extends FunSuite {
  val ep1 = Endpoint(123, 123, "service1")
  val ep2 = Endpoint(234, 234, "service2")
  val ep3 = Endpoint(345, 345, "service3")
  val ann1 = Annotation(100, thrift.Constants.CLIENT_SEND, Some(ep1))
  val ann2 = Annotation(150, thrift.Constants.CLIENT_RECV, Some(ep1))
  val spans1 = List(Span(1, "methodcall", 666, None, List(ann1, ann2), Nil))
  val trace1 = Trace(spans1)
  // duration 50

  val ann3 = Annotation(101, thrift.Constants.CLIENT_SEND, Some(ep2))
  val ann4 = Annotation(501, thrift.Constants.CLIENT_RECV, Some(ep2))
  val spans2 = List(Span(2, "methodcall", 667, None, List(ann3, ann4), Nil))
  val trace2 = Trace(spans2)
  // duration 400

  val ann5 = Annotation(99, thrift.Constants.CLIENT_SEND, Some(ep3))
  val ann6 = Annotation(199, thrift.Constants.CLIENT_RECV, Some(ep3))
  val spans3 = List(Span(3, "methodcall", 668, None, List(ann5, ann6), Nil))
  val trace3 = Trace(spans3)
  // duration 100

  // get some server action going on
  val ann7 = Annotation(110, thrift.Constants.SERVER_RECV, Some(ep2))
  val ann8 = Annotation(140, thrift.Constants.SERVER_SEND, Some(ep2))
  val spans4 = List(
    Span(2, "methodcall", 666, None, List(ann1, ann2), Nil),
    Span(2, "methodcall", 666, None, List(ann7, ann8), Nil))
  val trace4 = Trace(spans4)

  val ann9 = Annotation(60, thrift.Constants.CLIENT_SEND, Some(ep3))
  val ann10 = Annotation(65, "annotation", Some(ep3))
  val ann11 = Annotation(100, thrift.Constants.CLIENT_RECV, Some(ep3))
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
    intercept[thrift.QueryException] {
      Await.result(svc.getTraceIdsBySpanName(null, "span", 1000, 100, thrift.Order.DurationDesc))
    }

    val actual = Await.result(svc.getTraceIdsBySpanName("service2", "methodcall", 1000, 50, thrift.Order.DurationDesc))
    assert(actual === Seq(2, 2, 2))
  }

  test("order results") {
    val svc = newLoadedService()

    // desc
    val actualDesc = Await.result(svc.getTraceIdsByServiceName("service3", 1000, 50, thrift.Order.DurationDesc))
    assert(actualDesc === Seq(3, 5))

    // asc
    val actualAsc = Await.result(svc.getTraceIdsByServiceName("service3", 1000, 50, thrift.Order.DurationAsc))
    assert(actualAsc === Seq(5, 3))

    // none
    val actualNone = Await.result(svc.getTraceIdsByServiceName("service3", 1000, 50, thrift.Order.None))
    assert(actualNone === Seq(3, 5))
  }

  test("trace summary for trace id") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceSummariesByIds(List(1), List()))
    assert(actual === List(TraceSummary(1, 100, 150, 50, Map("service1" -> 1), List(ep1)).toThrift))
  }

  test("trace combo for trace id") {
    val svc = newLoadedService()

    val trace = trace1.toThrift
    val summary = TraceSummary(1, 100, 150, 50, Map("service1" -> 1), List(ep1)).toThrift
    val timeline = TraceTimeline(trace1) map { _.toThrift }
    val combo = thrift.TraceCombo(trace, Some(summary), timeline, Some(Map(666L -> 1)))

    val actual = Await.result(svc.getTraceCombosByIds(List(1), List()))
    assert(actual === Seq(combo))
  }

  test("find trace ids by service name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByServiceName("service3", 1000, 50, thrift.Order.DurationDesc))
    assert(actual === Seq(3, 5))
  }

  test("find trace ids by annotation name") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByAnnotation("service3", "annotation", null, 1000, 50, thrift.Order.DurationDesc))
    assert(actual === Seq(5))
  }

  test("find trace ids by annotation name and value") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTraceIdsByAnnotation("service3", "annotation", ByteBuffer.wrap("ann".getBytes), 1000, 50, thrift.Order.DurationDesc))
    assert(actual === Seq(5))
  }

  test("get trace by traceId") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getTracesByIds(List(1L), List()))
    assert(actual === List(trace1.toThrift))
  }

  test("get timeline by traceId") {
    val svc = newLoadedService()

    val ann1 = thrift.TimelineAnnotation(100, thrift.Constants.CLIENT_SEND,
      ep1.toThrift, 666, None, "service1", "methodcall")
    val ann2 = thrift.TimelineAnnotation(150, thrift.Constants.CLIENT_RECV,
      ep1.toThrift, 666, None, "service1", "methodcall")
    val ann3 = thrift.TimelineAnnotation(101, thrift.Constants.CLIENT_SEND,
      ep2.toThrift, 667, None, "service2", "methodcall")
    val ann4 = thrift.TimelineAnnotation(501, thrift.Constants.CLIENT_RECV,
      ep2.toThrift, 667, None, "service2", "methodcall")
    val ann5 = thrift.TimelineAnnotation(110, thrift.Constants.SERVER_RECV,
      ep2.toThrift, 666, None, "service2", "methodcall")
    val ann6 = thrift.TimelineAnnotation(140, thrift.Constants.SERVER_SEND,
      ep2.toThrift, 666, None, "service2", "methodcall")

    val control = List(thrift.TraceTimeline(2L, 666, List(ann1, ann3, ann5, ann6, ann2, ann4), List()))
    val actual = Await.result(svc.getTraceTimelinesByIds(List(2L), List(thrift.Adjust.Nothing, thrift.Adjust.TimeSkew)))
    assert(actual === control)
  }

  test("get span names") {
    val svc = newLoadedService()
    val actual = Await.result(svc.getSpanNames("service3"))
    assert(actual === Set("methodcall", "otherMethod"))
  }

  test("get/set trace TTL") {
    val svc = newLoadedService()

    val original = Await.result(svc.getTraceTimeToLive(1))
    assert(original === 1)

    Await.ready(svc.setTraceTimeToLive(1, 100))
    val newVal = Await.result(svc.getTraceTimeToLive(1))
    assert(newVal === 100)
  }
}
