package com.twitter.zipkin.web

import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}
import org.specs.Specification
import com.twitter.zipkin.common.json.ZipkinJson

class JsonSerializationSpec extends Specification {
  "Jerkson" should {
    "serialize" in {
      "span with no annotations" in {
        val s = Span(1L, "Unknown", 2L, None, List.empty[Annotation], List.empty[BinaryAnnotation], false)
        ZipkinJson.generate(s) mustNot throwAnException
      }
    }
  }
}
