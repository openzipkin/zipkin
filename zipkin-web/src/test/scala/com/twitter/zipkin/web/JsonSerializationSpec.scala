package com.twitter.zipkin.web

import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}
import com.codahale.jerkson.Json
import org.specs.Specification

class JsonSerializationSpec extends Specification {
  "Jerkson" should {
    "serialize" in {
      "span with no annotations" in {
        val s = Span(1L, "Unknown", 2L, None, List.empty[Annotation], List.empty[BinaryAnnotation], false)
        Json.generate(s.toJson) mustNot throwAnException
      }
    }
  }
}
