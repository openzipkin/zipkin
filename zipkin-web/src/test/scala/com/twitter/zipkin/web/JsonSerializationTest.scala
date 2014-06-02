package com.twitter.zipkin.web

import com.twitter.zipkin.common.json.ZipkinJson
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JsonSerializationTest extends FunSuite {
  test("serialize span with no annotations") {
    val s = Span(1L, "Unknown", 2L, None, List.empty[Annotation], List.empty[BinaryAnnotation], false)
    // will not throw an exception
    (new ZipkinJson).generate(s)
  }
}
