/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.twitter.zipkin.hadoop


import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.scalding._
import gen.AnnotationType
import scala.collection.JavaConverters._
import scala.collection.mutable._
import sources.{PreprocessedSpanSource, Util}

/**
* Tests that Timeouts finds the service calls where timeouts occur and how often
* the timeouts occur per type of service
*/

class TimeoutsSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(1234, 6666, "service1")
  val endpoint2 = new gen.Endpoint(12345, 111, "service2")

  val span = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint),
      new gen.Annotation(2001, "finagle.timeout")).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service", "service")

  val span1 = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(2000, "sr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service1", "service1")

  val span2 = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint2), new gen.Annotation(2000, "sr").setHost(endpoint2),
      new gen.Annotation(2001, "finagle.timeout")).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava, "service2", "service2")


  "Timeouts" should {
    "find service calls with timeouts" in {
      JobTest("com.twitter.zipkin.hadoop.Timeouts").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        arg("error_type", "finagle.timeout").
        source(PreprocessedSpanSource(), (Util.repeatSpan(span, 101, 120, 1) ::: (Util.repeatSpan(span1, 20, 300, 102)) ::: (Util.repeatSpan(span2, 30, 400, 300)))).
        sink[(String, String, Long)](Tsv("outputFile")) {
        val map = new HashMap[String, Long]()
        map("service, Unknown Service Name") = 0
        map("service2, service1") = 0
        map("service2, Unknown Service Name") = 0
        outputBuffer => outputBuffer foreach { e =>
          map(e._1 + ", " + e._2) = e._3
        }
        map("service, Unknown Service Name") mustEqual 102
        map("service2, service1") mustEqual 21
        map("service2, Unknown Service Name") mustEqual 10
      }.run.finish
    }
  }
}

