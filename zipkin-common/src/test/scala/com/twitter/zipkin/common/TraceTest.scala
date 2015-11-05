package com.twitter.zipkin.common

import com.twitter.zipkin.Constants
import org.scalatest.FunSuite

class TraceTest extends FunSuite {
  import com.twitter.zipkin.common.Trace._

  test("get duration") {
    val annotations = List(
      Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span = Span(12345, "methodcall", 666, None, annotations)
    assert(duration(List(span)) === Some(100))
  }

  /** Duration cannot be calculated with a single point. */
  test("get duration empty when an incomplete single span") {
    val anno = Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1")))
    val span = Span(12345, "methodcall", 666, None, List(anno))
    assert(duration(List(span)) === None)
  }

  test("get duration empty when multiple incomplete spans with same timestamp") {
    val anno = Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1")))
    val span1 = Span(12345, "methodcall", 666, None, List(anno))
    val span2 = span1.copy(id = 667)
    assert(duration(List(span1, span2)) === None)
  }

  /** Duration is at least the distance between in-flight spans. */
  test("get duration is interval between multiple incomplete annotations") {
    val anno = Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1")))
    val span1 = Span(12345, "methodcall", 666, None, List(anno))
    val span2 = span1.copy(id = 667, annotations = List(anno.copy(timestamp = 150)))
    val span3 = span1.copy(id = 668, annotations = List(anno.copy(timestamp = 200)))
    assert(duration(List(span1, span2, span3)) === Some(100))
  }

  test("get duration without root span") {
    val annotations = List(
      Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span1 = Span(12345, "methodcall", 666, Some(123), annotations)
    val annotations2 = List(
      Annotation(150, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(160, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span2 = span1.copy(id = 667, annotations = annotations2)
    assert(duration(List(span1, span2)) === Some(100))
  }

  test("get duration for imbalanced spans") {
    val ann1 = List(
      Annotation(0, "Client send", None)
    )
    val ann2 = List(
      Annotation(1, "Server receive", None),
      Annotation(12, "Server send", None)
    )

    val span1 = Span(123, "method_1", 100, None, ann1)
    val span2 = Span(123, "method_2", 200, Some(100), ann2)

    assert(duration(List(span1, span2)) === Some(12))
  }
}
