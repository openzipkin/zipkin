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
import collection.mutable.HashMap
import sources.{PrepTsvSource, PreprocessedSpanSource, Util}

/**
* Tests that DependencyTree finds all service calls and how often per pair
* of endpoints
*/

class DependencyTreeSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service2")
  val span = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava, "service", "service")
  val span1 = new gen.SpanServiceName(123456, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "sr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service1", "service1")
  val span2 = new gen.SpanServiceName(1234567, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint2), new gen.Annotation(3000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service2", "service2")

  val spans = Util.repeatSpan(span, 30, 40, 1) ++ Util.repeatSpan(span1, 50, 200, 40)

  "DependencyTree" should {
    "Find the number of calls between endpoints" in {
      JobTest("com.twitter.zipkin.hadoop.DependencyTree")
        .arg("input", "inputFile")
        .arg("output", "outputFile")
        .arg("date", "2012-01-01T01:00")
        .source(PreprocessedSpanSource(), spans)
        .source(PrepTsvSource(), Util.getSpanIDtoNames(spans))
        .sink[(String, String, Long)](Tsv("outputFile")) {
        val map = new HashMap[String, Long]()
        map("service, Unknown Service Name") = 0
        map("service1, service") = 0
        map("service1, Unknown Service Name") = 0
        outputBuffer => outputBuffer foreach { e =>
          map(e._1 + ", " + e._2) = e._3
        }
        map("service, Unknown Service Name") mustEqual 31
        map("service1, service") mustEqual 31
        map("service1, Unknown Service Name") mustEqual 20
      }.run.finish
    }
  }
}

