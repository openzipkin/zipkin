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
import com.twitter.zipkin.hadoop.sources.{PrepTsvSource, PreprocessedSpanSource, Util}

/**
 * Tests that DependencyTree finds all service calls and how often per pair
 * of endpoints
 */

class WorstRuntimesPerTraceSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service1")
  val span = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava, "service")
  val span1 = new gen.SpanServiceName(12345, "methodcall", 444,
    List(new gen.Annotation(2000, "cs").setHost(endpoint1), new gen.Annotation(4000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service1")
  val span2 = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(10000, "cs").setHost(endpoint2), new gen.Annotation(13000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service1")

  val spans = Util.repeatSpan(span, 1, 40, 1) ++ Util.repeatSpan(span1, 1, 200, 40) ++ Util.repeatSpan(span2, 0, 40, 1)

  "WorstRuntimesPerTrace" should {
    "Find services which took the longest time per trace" in {
      JobTest("com.twitter.zipkin.hadoop.WorstRuntimesPerTrace")
        .arg("input", "inputFile")
        .arg("output", "outputFile")
        .arg("date", "2012-01-01T01:00")
        .source(PreprocessedSpanSource(), spans)
        .sink[(String, Long, Int)](Tsv("outputFile")) {
        val map = new HashMap[String, Int]()
        outputBuffer => outputBuffer foreach { e =>
          map(e._1 + ", " + e._2) = e._3
        }
        map("service, 12345") mustEqual 2
        map("service1, 12345") mustEqual 7
      }.run.finish
    }
  }


}