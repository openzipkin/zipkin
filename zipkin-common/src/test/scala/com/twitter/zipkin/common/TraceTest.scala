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
package com.twitter.zipkin.common

import com.twitter.zipkin.Constants
import org.scalatest.FunSuite

import scala.collection.mutable

class TraceTest extends FunSuite {

  // TODO these don't actually make any sense
  val annotations1 = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
    Annotation(150, Constants.ClientRecv, Some(Endpoint(456, 456, "service1"))))
  val annotations2 = List(Annotation(200, Constants.ClientSend, Some(Endpoint(456, 456, "service2"))),
    Annotation(250, Constants.ClientRecv, Some(Endpoint(123, 123, "service2"))))
  val annotations3 = List(Annotation(300, Constants.ClientSend, Some(Endpoint(456, 456, "service2"))),
    Annotation(350, Constants.ClientRecv, Some(Endpoint(666, 666, "service2"))))
  val annotations4 = List(Annotation(400, Constants.ClientSend, Some(Endpoint(777, 777, "service3"))),
    Annotation(500, Constants.ClientRecv, Some(Endpoint(888, 888, "service3"))))

  val span1Id = 666L
  val span2Id = 777L
  val span3Id = 888L
  val span4Id = 999L

  val span1 = Span(12345, "methodcall1", span1Id, None, annotations1)
  val span2 = Span(12345, "methodcall2", span2Id, Some(span1Id), annotations2)
  val span3 = Span(12345, "methodcall2", span3Id, Some(span2Id), annotations3)
  val span4 = Span(12345, "methodcall2", span4Id, Some(span3Id), annotations4)
  val span5 = Span(12345, "methodcall4", 1111L, Some(span4Id)) // no annotations

  val trace = Trace(List[Span](span1, span2, span3, span4))

  test("get duration of trace") {
    val annotations = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span = Span(12345, "methodcall", 666, None, annotations)
    assert(Trace(List(span)).duration === 100)
  }

  test("get duration of trace without root span") {
    val annotations = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span = Span(12345, "methodcall", 666, Some(123), annotations)
    val annotations2 = List(Annotation(150, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(160, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span2 = Span(12345, "methodcall", 666, Some(123), annotations2)
    assert(Trace(List(span, span2)).duration === 100)
  }

  test("get correct duration for imbalanced spans") {
    val ann1 = List(
      Annotation(0, "Client send", None)
    )
    val ann2 = List(
      Annotation(1, "Server receive", None),
      Annotation(12, "Server send", None)
    )

    val span1 = Span(123, "method_1", 100, None, ann1)
    val span2 = Span(123, "method_2", 200, Some(100), ann2)

    val trace = new Trace(Seq(span1, span2))
  }

  test("return root span") {
    assert(trace.getRootSpan === Some(span1))
  }

  test("getIdToChildrenMap") {
    val map = new mutable.HashMap[Long, mutable.Set[Span]] with mutable.MultiMap[Long, Span]
    map.addBinding(span1Id, span2)
    map.addBinding(span2Id, span3)
    map.addBinding(span3Id, span4)
    assert(trace.getIdToChildrenMap === map)
  }

  test("getSpanTree") {
    val spanTreeEntry4 = SpanTreeEntry(span4, List[SpanTreeEntry]())
    val spanTreeEntry3 = SpanTreeEntry(span3, List[SpanTreeEntry](spanTreeEntry4))
    val spanTreeEntry2 = SpanTreeEntry(span2, List[SpanTreeEntry](spanTreeEntry3))
    val spanTreeEntry1 = SpanTreeEntry(span1, List[SpanTreeEntry](spanTreeEntry2))
    assert(trace.getSpanTree(trace.getRootSpan.get, trace.getIdToChildrenMap) === spanTreeEntry1)
  }

  test("instantiate with SpanTreeEntry") {
    val spanTree = trace.getSpanTree(trace.getRootSpan.get, trace.getIdToChildrenMap)
    val actualTrace = Trace(spanTree)
    assert(trace === actualTrace)
  }

  test("return duration 0 due to missing annotations") {
    // no annotation at all
    val spanNoAnn = Span(1, "method", 123L)
    val noAnnTrace = Trace(List(spanNoAnn))
    assert(noAnnTrace.duration === 0L)
  }

  test("sort spans by first annotation timestamp") {
    val inputSpans = List[Span](span4, span3, span5, span1, span2)
    val expectedTrace = Trace(List[Span](span1, span2, span3, span4, span5))
    val actualTrace = Trace(inputSpans)

    assert(expectedTrace.spans === actualTrace.spans)
  }

  test("merged spans are sorted") {
    val ann1 = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(300, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val ann2 = List(Annotation(150, Constants.ServerRecv, Some(Endpoint(456, 456, "service2"))),
      Annotation(200, Constants.ServerSend, Some(Endpoint(456, 456, "service2"))))

    val annMerged = List(
      Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(150, Constants.ServerRecv, Some(Endpoint(456, 456, "service2"))),
      Annotation(200, Constants.ServerSend, Some(Endpoint(456, 456, "service2"))),
      Annotation(300, Constants.ClientRecv, Some(Endpoint(123, 123, "service1")))
    )

    val spanToMerge1 = Span(12345, "methodcall2", span2Id, Some(span1Id), ann1)
    val spanToMerge2 = Span(12345, "methodcall2", span2Id, Some(span1Id), ann2)
    val spanMerged = Span(12345, "methodcall2", span2Id, Some(span1Id), annMerged)

    assert(Trace(List(spanMerged)).spans === Trace(List(spanToMerge1, spanToMerge2)).spans)
  }
}
