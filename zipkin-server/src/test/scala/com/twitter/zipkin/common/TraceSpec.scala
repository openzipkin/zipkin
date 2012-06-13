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

import org.specs.Specification
import com.twitter.zipkin.gen
import collection.mutable
import java.nio.ByteBuffer
import com.twitter.zipkin.adapter.ThriftAdapter

class TraceSpec extends Specification {

  // TODO these don't actually make any sense
  val annotations1 = List(Annotation(100, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
    Annotation(150, gen.Constants.CLIENT_RECV, Some(Endpoint(456, 456, "service1"))))
  val annotations2 = List(Annotation(200, gen.Constants.CLIENT_SEND, Some(Endpoint(456, 456, "service2"))),
    Annotation(250, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service2"))))
  val annotations3 = List(Annotation(300, gen.Constants.CLIENT_SEND, Some(Endpoint(456, 456, "service2"))),
    Annotation(350, gen.Constants.CLIENT_RECV, Some(Endpoint(666, 666, "service2"))))
  val annotations4 = List(Annotation(400, gen.Constants.CLIENT_SEND, Some(Endpoint(777, 777, "service3"))),
    Annotation(500, gen.Constants.CLIENT_RECV, Some(Endpoint(888, 888, "service3"))))

  val span1Id = 666L
  val span2Id = 777L
  val span3Id = 888L
  val span4Id = 999L

  val span1 = Span(12345, "methodcall1", span1Id, None, annotations1, Nil)
  val span2 = Span(12345, "methodcall2", span2Id, Some(span1Id), annotations2, Nil)
  val span3 = Span(12345, "methodcall2", span3Id, Some(span2Id), annotations3, Nil)
  val span4 = Span(12345, "methodcall2", span4Id, Some(span3Id), annotations4, Nil)
  val span5 = Span(12345, "methodcall4", 1111L, Some(span4Id), List(), Nil) // no annotations

  val trace = Trace(List[Span](span1, span2, span3, span4))

  "Trace" should {
    "convert to thrift and back" in {
      val span = Span(12345, "methodcall", 666, None,
        List(Annotation(1, "boaoo", None)), Nil)
      val expectedTrace = Trace(List[Span](span))
      val thriftTrace = expectedTrace.toThrift
      val actualTrace = Trace.fromThrift(thriftTrace)
      expectedTrace mustEqual actualTrace
    }

    "get duration of trace" in {
      val annotations = List(Annotation(100, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
        Annotation(200, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service1"))))
      val span = Span(12345, "methodcall", 666, None,
        annotations, Nil)
      100 mustEqual Trace(List(span)).duration
    }

    "get duration of trace without root span" in {
      val annotations = List(Annotation(100, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
        Annotation(200, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service1"))))
      val span = Span(12345, "methodcall", 666, Some(123),
        annotations, Nil)
      val annotations2 = List(Annotation(150, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
        Annotation(160, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service1"))))
      val span2 = Span(12345, "methodcall", 666, Some(123),
        annotations2, Nil)
      100 mustEqual Trace(List(span, span2)).duration
    }

    "get correct duration for imbalanced spans" in {
      val ann1 = List(
        Annotation(0, "Client send", None)
      )
      val ann2 = List(
        Annotation(1, "Server receive", None),
        Annotation(12, "Server send", None)
      )

      val span1 = Span(123, "method_1", 100, None, ann1, Nil)
      val span2 = Span(123, "method_2", 200, Some(100), ann2, Nil)

      val trace = new Trace(Seq(span1, span2))
      val duration = trace.toTraceSummary.get.durationMicro
      duration mustEqual 12
    }

    "get services involved in trace" in {
      val expectedServices = Set("service1", "service2", "service3")
      expectedServices mustEqual Trace(List(span1, span2, span3, span4)).services
    }

    "get endpooints involved in trace" in {
      val expectedEndpoints = Set(Endpoint(123, 123, "service1"), Endpoint(123, 123, "service2"),
        Endpoint(456, 456, "service1"), Endpoint(456, 456, "service2"), Endpoint(666, 666, "service2"),
        Endpoint(777, 777, "service3"), Endpoint(888, 888, "service3"))
      expectedEndpoints mustEqual Trace(List(span1, span2, span3, span4)).endpoints
    }

    "return root span" in {
      Some(span1) mustEqual trace.getRootSpan
    }

    "getIdToChildrenMap" in {
      val map = new mutable.HashMap[Long, mutable.Set[Span]] with mutable.MultiMap[Long, Span]
      map.addBinding(span1Id, span2)
      map.addBinding(span2Id, span3)
      map.addBinding(span3Id, span4)
      map mustEqual trace.getIdToChildrenMap
    }

    "getBinaryAnnotations" in {
      val ba1 = gen.BinaryAnnotation("key1", ByteBuffer.wrap("value1".getBytes), gen.AnnotationType.String)
      val span1 = Span(1L, "", 1L, None, List(), List(ThriftAdapter(ba1)))
      val ba2 = gen.BinaryAnnotation("key2", ByteBuffer.wrap("value2".getBytes), gen.AnnotationType.String)
      val span2 = Span(1L, "", 2L, None, List(), List(ThriftAdapter(ba2)))

      val trace = Trace(List[Span](span1, span2))
      Seq(ba1, ba2) mustEqual trace.getBinaryAnnotations
    }

    "getSpanTree" in {
      val spanTreeEntry4 = SpanTreeEntry(span4, List[SpanTreeEntry]())
      val spanTreeEntry3 = SpanTreeEntry(span3, List[SpanTreeEntry](spanTreeEntry4))
      val spanTreeEntry2 = SpanTreeEntry(span2, List[SpanTreeEntry](spanTreeEntry3))
      val spanTreeEntry1 = SpanTreeEntry(span1, List[SpanTreeEntry](spanTreeEntry2))
      spanTreeEntry1 mustEqual trace.getSpanTree(trace.getRootSpan.get, trace.getIdToChildrenMap)
    }

    "instantiate with SpanTreeEntry" in {
      val spanTree = trace.getSpanTree(trace.getRootSpan.get, trace.getIdToChildrenMap)
      val actualTrace = Trace(spanTree)
      actualTrace mustEqual trace
    }

    "return none due to missing annotations" in {
      // no annotation at all
      val spanNoAnn = Span(1, "method", 123L, None, List(), Nil)
      val noAnnTrace = Trace(List(spanNoAnn))
      noAnnTrace.getStartAndEndTimestamp mustEqual None
    }

    "sort spans by first annotation timestamp" in {
      val inputSpans = List[Span](span4, span3, span5, span1, span2)
      val expectedTrace = Trace(List[Span](span1, span2, span3, span4, span5))
      val actualTrace = Trace(inputSpans).sortedByTimestamp

      expectedTrace mustEqual actualTrace
    }

    "merge spans" in {
      val ann1 = List(Annotation(100, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
        Annotation(300, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service1"))))
      val ann2 = List(Annotation(150, gen.Constants.SERVER_RECV, Some(Endpoint(456, 456, "service2"))),
        Annotation(200, gen.Constants.SERVER_SEND, Some(Endpoint(456, 456, "service2"))))

      val annMerged = List(
        Annotation(100, gen.Constants.CLIENT_SEND, Some(Endpoint(123, 123, "service1"))),
        Annotation(300, gen.Constants.CLIENT_RECV, Some(Endpoint(123, 123, "service1"))),
        Annotation(150, gen.Constants.SERVER_RECV, Some(Endpoint(456, 456, "service2"))),
        Annotation(200, gen.Constants.SERVER_SEND, Some(Endpoint(456, 456, "service2")))
        )

      val spanToMerge1 = Span(12345, "methodcall2", span2Id, Some(span1Id), ann1, Nil)
      val spanToMerge2 = Span(12345, "methodcall2", span2Id, Some(span1Id), ann2, Nil)
      val spanMerged = Span(12345, "methodcall2", span2Id, Some(span1Id), annMerged, Nil)

      Trace(List(spanMerged)) mustEqual Trace(List(spanToMerge1, spanToMerge2)).mergeSpans
    }

    "get rootmost span from full trace" in {
      val spanNoneParent = Span(1, "", 100, None, List(), Nil)
      val spanParent = Span(1, "", 200, Some(100), List(), Nil)
      Trace(List(spanParent, spanNoneParent)).getRootMostSpan mustEqual Some(spanNoneParent)
    }

    "get rootmost span from trace without real root" in {
      val spanNoParent = Span(1, "", 100, Some(0), List(), Nil)
      val spanParent = Span(1, "", 200, Some(100), List(), Nil)
      Trace(List(spanParent, spanNoParent)).getRootMostSpan mustEqual Some(spanNoParent)
    }

    "get span depths for trace" in {
      trace.toSpanDepths mustEqual Some(Map(666 -> 1, 777 -> 2, 888 -> 3, 999 -> 4))
    }

    "get no span depths for empty trace" in {
      Trace(List()).toSpanDepths mustEqual None
    }

    "get start and end timestamp" in {
      val ann1 = Annotation(1, "hello", None)
      val ann2 = Annotation(43, "goodbye", None)

      val span1 = Span(12345, "methodcall", 6789, None, List(), Nil)
      val span2 = Span(12345, "methodcall_2", 345, None, List(ann1, ann2), Nil)

      val span3 = Span(23456, "methodcall_3", 12, None, List(ann1), Nil)
      val span4 = Span(23456, "methodcall_4", 34, None, List(ann2), Nil)

      // no spans
      val trace1 = new Trace(List())
      trace1.getStartAndEndTimestamp mustEqual None

      // 1 span, 0 annotations
      val trace2 = new Trace(List(span1))
      trace2.getStartAndEndTimestamp mustEqual None

      val trace3 = new Trace(List(span1, span2))
      trace3.getStartAndEndTimestamp mustEqual Some(Timespan(1, 43))

      val trace4 = new Trace(List(span3, span4))
      trace4.getStartAndEndTimestamp mustEqual Some(Timespan(1, 43))
    }

    "get service counts" in {
      val ep1 = Some(Endpoint(1, 1, "ep1"))
      val ep2 = Some(Endpoint(2, 2, "ep2"))
      val ep3 = Some(Endpoint(3, 3, "ep3"))

      val ann1 = Annotation(1, "ann1", ep1)
      val ann2 = Annotation(2, "ann2", ep2)
      val ann3 = Annotation(3, "ann3", ep3)
      val ann4 = Annotation(4, "ann4", ep2)

      val span1 = Span(1234, "method1", 5678, None, List(ann1, ann2, ann3, ann4), Nil)
      val span2 = Span(1234, "method2", 345, None, List(ann4), Nil)
      val trace1 = new Trace(Seq(span1, span2))

      val expected = Map("ep1" -> 1, "ep2" -> 2, "ep3" -> 1)
      trace1.serviceCounts mustEqual expected
    }
  }
}
