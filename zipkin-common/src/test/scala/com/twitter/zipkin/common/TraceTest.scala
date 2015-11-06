package com.twitter.zipkin.common

import org.scalatest.FunSuite

class TraceTest extends FunSuite {
  import com.twitter.zipkin.common.Trace._

  test("get duration") {
    val span = Span(12345, "methodcall", 666, None, Some(100), Some(100))
    assert(duration(List(span)) === Some(100))
  }

  /** Duration cannot be calculated with a single point. */
  test("get duration empty when an incomplete single span") {
    val span = Span(12345, "methodcall", 666, None, Some(100L))
    assert(duration(List(span)) === None)
  }

  test("get duration empty when multiple incomplete spans with same timestamp") {
    val span1 = Span(12345, "methodcall", 666, None, Some(100L))
    val span2 = span1.copy(id = 667)
    assert(duration(List(span1, span2)) === None)
  }

  /** Duration is at least the distance between in-flight spans. */
  test("get duration is interval between multiple incomplete annotations") {
    val span1 = Span(12345, "methodcall", 666, None, Some(100L))
    val span2 = span1.copy(id = 667, timestamp = Some(150L))
    val span3 = span1.copy(id = 668, timestamp = Some(200L))
    assert(duration(List(span1, span2, span3)) === Some(100))
  }

  test("get duration for imbalanced spans") {
    val span1 = Span(123, "method_1", 100, None, Some(0))
    val span2 = Span(123, "method_2", 200, Some(100L), Some(1), Some(12))

    assert(duration(List(span1, span2)) === Some(13))
  }
}
