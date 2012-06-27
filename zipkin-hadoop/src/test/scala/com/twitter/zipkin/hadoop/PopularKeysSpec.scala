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
import sources.PrepSpanSource
import scala.collection.JavaConverters._
import scala.collection.mutable._

/**
 * Tests that PopularKeys finds the most popular keys per service
 */

class PopularKeysSpec extends Specification with TupleConversions {

  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val span = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List(new gen.BinaryAnnotation("hi", null, AnnotationType.BOOL)).asJava)
  val span1 = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "sr").setHost(endpoint), new gen.Annotation(2000, "cr").setHost(endpoint)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava)


  "PopularKeys" should {
    "return a map with correct entries for each key" in {
      JobTest("com.twitter.zipkin.hadoop.PopularKeys").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(PrepSpanSource(), repeatSpan(span, 101, 0) ::: repeatSpan(span1, 50, 200)).
        sink[(String, String, Int)](Tsv("outputFile")) {
        val map = new HashMap[String, Int]()
        outputBuffer => outputBuffer foreach { e =>
          println(e)
          map(e._1 + e._2) = e._3
        }
        map("servicebye") mustEqual 51
        map("servicehi") mustEqual 102
      }.run.finish
    }

  }

  def repeatSpan(span: gen.Span, count: Int, offset : Int): List[(gen.Span, Int)] = {
    ((0 to count).toSeq map { i: Int => span.deepCopy().setId(i + offset) -> (i + offset)}).toList
  }

}

