package com.twitter.zipkin.adjuster

import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import org.scalatest.FunSuite

class MergeByIdTest extends FunSuite {

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

    val spanToMerge1 = Span(12345, "methodcall2", 2, Some(1), ann1)
    val spanToMerge2 = Span(12345, "methodcall2", 2, Some(1), ann2)
    val spanMerged = Span(12345, "methodcall2", 2, Some(1), annMerged)

    assert(MergeById(List(spanToMerge1, spanToMerge2)) == List(spanMerged))
  }

  /** Missing timestamp means the span cannot be placed on a timeline */
  test("filters spans without a timestamp") {
    assert(MergeById(List(Span(12345, "methodcall2", 2))) == List())
  }
}
