package com.twitter.zipkin.web

import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import org.scalatest.FunSuite

class TraceSummaryTest extends FunSuite {

  val ep1 = Endpoint(123, 123, "service1")
  val ep2 = Endpoint(456, 456, "service2")
  val ep3 = Endpoint(666, 666, "service2")
  val ep4 = Endpoint(777, 777, "service3")
  val ep5 = Endpoint(888, 888, "service3")

  val annotations1 = List(Annotation(100, Constants.ClientSend, Some(ep1)),
    Annotation(150, Constants.ClientRecv, Some(ep1)))
  val annotations2 = List(Annotation(200, Constants.ClientSend, Some(ep2)),
    Annotation(250, Constants.ClientRecv, Some(ep2)))
  val annotations3 = List(Annotation(300, Constants.ClientSend, Some(ep2)),
    Annotation(350, Constants.ClientRecv, Some(ep3)))
  val annotations4 = List(Annotation(400, Constants.ClientSend, Some(ep4)),
    Annotation(500, Constants.ClientRecv, Some(ep5)))

  val span1Id = 666L
  val span2Id = 777L
  val span3Id = 888L
  val span4Id = 999L

  val span1 = Span(12345, "methodcall1", span1Id, None, annotations1)
  val span2 = Span(12345, "methodcall2", span2Id, Some(span1Id), annotations2)
  val span3 = Span(12345, "methodcall2", span3Id, Some(span2Id), annotations3)
  val span4 = Span(12345, "methodcall2", span4Id, Some(span3Id), annotations4)
  val span5 = Span(12345, "methodcall4", 1111L, Some(span4Id)) // no annotations

  val trace = List(span1, span2, span3, span4)

  test("none when no spans") {
    assert(TraceSummary(List()) === None)
  }

  test("none when no annotations") {
    assert(TraceSummary(List(span5)) === None)
  }

  test("dedupes duplicate endpoints") {
    val summary = TraceSummary(trace).get

    assert(summary.endpoints == List(ep1, ep2, ep3, ep4, ep5))
  }

  test("timestamp and duration") {
    val summary = TraceSummary(trace).get

    assert(summary.timestamp == 100L)
    assert(summary.duration == 400L)
  }

  test("get span depths from trace") {
    val spanNoneParent = Span(1, "", 100)
    val spanParent = Span(1, "", 200, Some(100))
    assert(TraceSummary.toSpanDepths(List(spanParent, spanNoneParent)) === Map(100 -> 1, 200 -> 2))
  }

  test("get span depths from trace without real root") {
    val spanNoParent = Span(1, "", 100, Some(0)) // 0 isn't present!
    val spanParent = Span(1, "", 200, Some(100))
    assert(TraceSummary.toSpanDepths(List(spanParent, spanNoParent)) === Map(100 -> 1, 200 -> 2))
  }

  test("get no span depths for empty trace") {
    assert(TraceSummary.toSpanDepths(List()) === Map.empty)
  }
}
