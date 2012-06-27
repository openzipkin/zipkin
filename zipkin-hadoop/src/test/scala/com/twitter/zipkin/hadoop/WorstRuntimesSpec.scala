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
import sources.SpanSource
import scala.collection.JavaConverters._
import collection.mutable.HashMap
import com.twitter.zipkin.gen.AnnotationType

/**
 * Tests that WorstRuntimes finds the spans which take the longest to run
 * per service
 */

class WorstRuntimesSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service2")
  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava)
  val span1 = new gen.Span(123456, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)
  val span2 = new gen.Span(1234567, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint2), new gen.Annotation(3000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)


  "WorstRuntimes" should {
    "return 100 entries with runtimes 1" in {
      JobTest("com.twitter.zipkin.hadoop.WorstRuntimes")
        .arg("input", "inputFile")
        .arg("output", "outputFile")
        .arg("date", "2012-01-01T01:00")
        .source(SpanSource(), (repeatSpan(span, 20, 0) ++ repeatSpan(span1, 20, 100)))
        .sink[(Long, String, Long)](Tsv("outputFile")) {
        var counts = new HashMap[String, Long]()
        counts += ("service" -> 0)
        counts += ("service1" -> 0)
        counts += ("service2" -> 0)
        outputBuffer => outputBuffer foreach { e =>
          println(e)
          if (e._2 == "service") {
            e._3 mustEqual 1
          } else if (e._2 == "service1") {
            e._3 mustEqual 3
          }
          counts(e._2) += 1
        }
        counts("service") mustEqual 21
        counts("service1") mustEqual 21
      }.run.finish
    }

  }

  def repeatSpan(span: gen.Span, count: Int, offset : Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset) -> (i + offset)}).toList
  }
}
