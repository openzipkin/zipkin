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
import java.nio.ByteBuffer
import sources._

/**
 * Tests that MemcacheRequest finds, per service and memcache request type, the number
 * of memcache requests
 */

class MemcacheRequestSpec extends Specification with TupleConversions {

  noDetailedDiffs()

  implicit val dateRange = DateRange(RichDate(123), RichDate(321))

  val endpoint = new gen.Endpoint(123, 666, "service")
  val endpoint1 = new gen.Endpoint(123, 666, "service1")
  val endpoint2 = new gen.Endpoint(123, 666, "service2")
  val span = new gen.SpanServiceName(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava, "service")
  val span1 = new gen.Span(12345, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("memcached.keys", ByteBuffer.allocate(4).putInt(0, 10), AnnotationType.BOOL)).asJava)
  val span2 = new gen.SpanServiceName(12346, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint), new gen.Annotation(2000, "sr").setHost(endpoint)).asJava,
    List[gen.BinaryAnnotation]().asJava, "service")
  val span3 = new gen.Span(123456, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("memcached.keys", ByteBuffer.allocate(4).putInt(0, 10), AnnotationType.BOOL)).asJava)
  val span4 = new gen.Span(123456, "methodcall", 666,
    List(new gen.Annotation(1000, "cs").setHost(endpoint1), new gen.Annotation(4000, "cr").setHost(endpoint1)).asJava,
    List(new gen.BinaryAnnotation("foobar", ByteBuffer.allocate(4).putInt(0, 10), AnnotationType.BOOL)).asJava)



  "MemcacheRequest" should {
    "find spans with memcache requests and their service names and labels" in {
      JobTest("com.twitter.zipkin.hadoop.MemcacheRequest").
        arg("input", "inputFile").
        arg("output", "outputFile").
        arg("date", "2012-01-01T01:00").
        source(PrepNoNamesSpanSource(TimeGranularity.Day), Util.repeatSpan(span1, 10, 100, 0) ++ Util.repeatSpan(span3, 2, 200, 300) ++ Util.repeatSpan(span3, 0, 1000, 500) ++ Util.repeatSpan(span4, 2, 1000, 11) ).
        source(PreprocessedSpanSource(TimeGranularity.Day), Util.repeatSpan(span, 12, 0, 20) ++ Util.repeatSpan(span2, 2, 300, 400) ++ Util.repeatSpan(span2, 0, 500, 100000)).
        sink[(String, Long)](Tsv("outputFile")) {
        val counts = new HashMap[String, Long]()
        counts("service") = 0
        counts("service1") = 0
        outputBuffer => outputBuffer foreach { e =>
          counts(e._1) += e._2
            println(e)
        }
        counts("service") mustEqual 2
        counts("service1") mustEqual 0
      }.run.finish
    }
  }
}

