package com.twitter.zipkin.adjuster

import com.twitter.zipkin.common._
import org.scalatest.FunSuite

class ApplyTimestampAndDurationTest extends FunSuite {

  test("noop when no annotations") {
    val span = Span(1, "n", 2, None, None, None)

    val adjusted = ApplyTimestampAndDuration(span)
    assert(adjusted.timestamp === None)
    assert(adjusted.duration === None)
  }

  test("duration is difference between annotation timestamps") {
    val span = Span(12345, "methodcall", 666, None, None, None, List(
      Annotation(1, "value1", Some(Endpoint(1, 2, "service"))),
      Annotation(2, "value2", Some(Endpoint(3, 4, "service"))),
      Annotation(3, "value3", Some(Endpoint(5, 6, "service")))
    ))

    val adjusted = ApplyTimestampAndDuration(span)
    assert(adjusted.timestamp === Some(1))
    assert(adjusted.duration === Some(2))
  }

  test("noop when duration already set") {
    val span = Span(12345, "methodcall", 666, None, Some(83L), Some(11L), List(
      Annotation(10, "value1", Some(Endpoint(1, 2, "service")))
    ))

    val adjusted = ApplyTimestampAndDuration(span)
    assert(adjusted === span)
  }

  /**
   * Spans subject to ApplyTimestampAndDuration are incomplete if they have
   * only one annotation. Rather than persist an unreliable timestamp, we leave
   * this alone until there's more data.
   */
  test("timestamp and duration are left unset when only one annotation") {
    val span = Span(1, "n", 2, None, None, None, annotations = List(
      Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
    ))

    val adjusted = ApplyTimestampAndDuration(span)
    assert(adjusted.timestamp === None)
    assert(adjusted.duration === None)
  }

  test("duration isn't set when only same timestamps") {
    val span = Span(1, "n", 2, None, None, None, annotations = List(
      Annotation(1, "value1", Some(Endpoint(1, 2, "service"))),
      Annotation(1, "value2", Some(Endpoint(1, 2, "service")))
    ))

    val adjusted = ApplyTimestampAndDuration(span)
    assert(adjusted.timestamp === Some(1))
    assert(adjusted.duration === None)
  }

  /** Missing timestamp means the span cannot be placed on a timeline */
  test("filters spans without a timestamp") {
    assert(ApplyTimestampAndDuration(List(Span(12345, "methodcall2", 2))) == List())
  }
}
