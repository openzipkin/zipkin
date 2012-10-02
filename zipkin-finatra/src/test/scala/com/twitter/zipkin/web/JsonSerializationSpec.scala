package com.twitter.zipkin.web

import org.specs.Specification
import com.twitter.zipkin.common.{Endpoint, Annotation}
import com.codahale.jerkson.Json

class JsonSerializationSpec extends Specification {
  "Jerkson" should {
    "serialize" in {
      "annotation with None duration" in {
        val a = Annotation(1L, "value", Some(Endpoint.Unknown), None)
        Json.generate(a)
      }

    }
  }
}
