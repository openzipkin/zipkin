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
import collection.mutable.{HashMap, HashSet}
import com.twitter.zipkin.hadoop.sources._
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * Tests that WhaleReport finds traces with 500 Internal Service Errors and finds the spans in those traces with finagle.retry or finagle.timeouts.
 */

class WhaleReportSpec extends Specification with TupleConversions {
  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val buf = ByteBuffer.allocate(100);

  // Create a character ByteBuffer
  val cbuf = buf.asCharBuffer();

  // Write a string
  cbuf.put(WhaleReport.ERROR_MESSAGE);

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service2")
  val span = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "finagle.timeout").setHost(endpoint), new gen.Annotation(1001, "sr").setHost(endpoint), new gen.Annotation(1002, "ss").setHost(endpoint), new gen.Annotation(1003, "cr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]( new gen.BinaryAnnotation("http.responsecode", buf, AnnotationType.BOOL ) ).asJava, "service")
  val span1 = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint2), new gen.Annotation(2000, "sr").setHost(endpoint2), new gen.Annotation(4000, "ss").setHost(endpoint2), new gen.Annotation(5000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service2")
  val span2 = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "finagle.retry").setHost(endpoint2), new gen.Annotation(3000, "cr").setHost(endpoint2)).asJava,
    List(new gen.BinaryAnnotation("bye", null, AnnotationType.BOOL)).asJava, "service2")

  val spans = (Util.repeatSpan(span, 0, 32, 1) ++ Util.repeatSpan(span1, 0, 100, 32) ++ Util.repeatSpan(span2, 0, 200, 100))

  "WhaleReport" should {
    "Return fail whales!" in {
      JobTest("com.twitter.zipkin.hadoop.WhaleReport").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(DailyPreprocessedSpanSource(), spans).
        sink[(Long, List[String])](Tsv("outputFile")) {
        var result = new HashSet[String]()
        var actual = new HashSet[String]()
        result += "service"
        result += "service2"
        outputBuffer => outputBuffer foreach { e =>
          e._1 mustEqual 12345
          for (name <- e._2)
            actual += name
        }
        actual mustEqual result
    }.run.finish
  }
}
}
