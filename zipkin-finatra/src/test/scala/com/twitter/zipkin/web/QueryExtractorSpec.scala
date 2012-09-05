/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.web

import com.twitter.finatra.Request
import com.twitter.util.Time
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification
import scala.collection.mutable

class QueryExtractorSpec extends Specification with JMocker with ClassMocker {

  def request(p: mutable.Map[String, String]) = new Request {
    params = p
  }

  "QueryExtractor" should {
    "require" in {
      "serviceName" in {
        val r = request(mutable.Map())
        QueryExtractor(r) mustBe None
      }
    }

    "parse params" in {
      val fmt = "MM-dd-yyyy HH:mm:ss"
      val t = Time.now
      val formatted = t.format(fmt)
      val r = request(mutable.Map(
        "serviceName" -> "myService",
        "spanName" -> "mySpan",
        "endDatetime" -> formatted,
        "limit" -> "1000"))
      val actual = QueryExtractor(r)
      actual mustNotBe None

      actual.get.serviceName mustBe "myService"
      actual.get.spanName mustNotBe None
      actual.get.spanName.get mustBe "mySpan"
      actual.get.endTs must_== new SimpleDateFormat(fmt).parse(formatted).getTime * 1000
      actual.get.limit must_== 1000
    }

    "have defaults for" in {
      "endDateTime" in Time.withCurrentTimeFrozen { tc =>
        val t = Time.now
        val r = request(mutable.Map("serviceName" -> "myService"))
        val actual = QueryExtractor(r)
        actual mustNotBe None
        actual.get.endTs must_== t.sinceEpoch.inMicroseconds
      }

      "limit" in {
        val r = request(mutable.Map("serviceName" -> "myService"))
        val actual = QueryExtractor(r)
        actual mustNotBe None
        actual.get.limit mustBe Constants.DefaultQueryLimit
      }
    }

    "parse spanName special cases" in {
      "all" in {
        val r = request(mutable.Map("serviceName" -> "myService", "spanName" -> "all"))
        val actual = QueryExtractor(r)
        actual mustNotBe None
        actual.get.spanName mustBe None
      }

      "" in {
        val r = request(mutable.Map("serviceName" -> "myService", "spanName" -> ""))
        val actual = QueryExtractor(r)
        actual mustNotBe None
        actual.get.spanName mustBe None
      }

      "valid" in {
        val r = request(mutable.Map("serviceName" -> "myService", "spanName" -> "something"))
        val actual = QueryExtractor(r)
        actual mustNotBe None
        actual.get.spanName mustNotBe None
        actual.get.spanName.get mustEq "something"
      }
    }

    "parse annotations" in {
      val r = request(mutable.Map(
        "serviceName" -> "myService",
        "annotations[0]" -> "finagle.retry",
        "annotations[1]" -> "finagle.timeout"))
      val actual = QueryExtractor(r)
      actual mustNotBe None
      actual.get.annotations mustNotBe None
      actual.get.annotations.get must_== Seq("finagle.retry", "finagle.timeout")
    }

    "parse key value annotations" in {
      val r = request(mutable.Map(
        "serviceName" -> "myService",
        "keyValueAnnotations[0][key]" -> "http.responsecode",
        "keyValueAnnotations[0][val]" -> "500"
      ))
      val actual = QueryExtractor(r)
      actual mustNotBe None
      actual.get.binaryAnnotations mustNotBe None
      actual.get.binaryAnnotations.get must_== Seq(BinaryAnnotation("http.responsecode", ByteBuffer.wrap("500".getBytes), AnnotationType.String, None))
    }
  }
}
