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

    val spanToMerge1 = Span(12345, "methodcall2", 2, Some(1L), None, None, ann1)
    val spanToMerge2 = Span(12345, "methodcall2", 2, Some(1L), None, None, ann2)
    val spanMerged = Span(12345, "methodcall2", 2, Some(1L), None, None, annMerged)

    assert(MergeById(List(spanToMerge1, spanToMerge2)) == List(spanMerged))
  }

  test("merged spans prefers client duration") {
    val server = Span(12345, "post", 2, Some(1L), Some(1457596859492000L), Some(11000), List(
      Annotation(1457596859492000L, Constants.ServerRecv, Some(Endpoint(456, 456, "service2"))),
      Annotation(1457596859503000L, Constants.ServerSend, Some(Endpoint(456, 456, "service2")))
    ))
    val client = Span(12345, "post", 2, Some(1L), Some(1457596859524000L), Some(15000), List(
      Annotation(1457596859524000L, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(1457596859539000L, Constants.ClientRecv, Some(Endpoint(123, 123, "service1")))
    ))
    assert(MergeById(List(server, client))(0).duration == client.duration)
  }
}
