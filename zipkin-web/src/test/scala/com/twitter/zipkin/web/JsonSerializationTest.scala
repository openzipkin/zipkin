package com.twitter.zipkin.web

import com.twitter.zipkin.common.json.ZipkinJson
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}
import org.scalatest.FunSuite

class JsonSerializationTest extends FunSuite {
  test("serialize span with no annotations") {
    val s = Span(1L, "Unknown", 2L, None, List.empty[Annotation], List.empty[BinaryAnnotation], false)
    // will not throw an exception
    (new ZipkinJson).generate(s)
  }
}
