package com.twitter.zipkin.collector.processor

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

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.zipkin.common.{Span, Endpoint, Annotation}
import com.twitter.ostrich.stats.{Metric, Histogram, Distribution, Stats}

class OstrichProcessorSpec extends Specification {
  val histogram = Histogram()
  histogram.add(10)
  val distribution = new Distribution(histogram)

  val prefix = "agg."

  "OstrichProcessor" should {
    "add two metrics if server span" in {
      val agg = new OstrichProcessor(prefix)

      val annotation1 = Annotation(10, gen.Constants.SERVER_RECV, Some(Endpoint(1, 2, "service")))
      val annotation2 = Annotation(20, gen.Constants.SERVER_SEND, Some(Endpoint(3, 4, "service")))
      val annotation3 = Annotation(30, "value3", Some(Endpoint(5, 6, "service")))

      val span = Span(12345, "methodcall", 666, None, List(annotation1, annotation2, annotation3), Nil)

      agg.processSpan(span)


      Stats.getMetrics()(prefix + "service") mustEqual distribution
      Stats.getMetrics()(prefix + "service.methodcall") mustEqual distribution
    }

    "add no metrics since not server span" in {
      val agg = new OstrichProcessor(prefix)

      val annotation1 = Annotation(10, gen.Constants.CLIENT_SEND, Some(Endpoint(1, 2, "service")))
      val annotation2 = Annotation(20, gen.Constants.CLIENT_RECV, Some(Endpoint(3, 4, "service")))
      val annotation3 = Annotation(30, "value3", Some(Endpoint(5, 6, "service")))

      val span = Span(12345, "methodcall", 666, None, List(annotation1, annotation2, annotation3), Nil)

      agg.processSpan(span)

      Stats.getMetrics()(prefix + "service") mustNotBe distribution
      Stats.getMetrics()(prefix + "service.methodcall") mustNotBe distribution
    }
  }
}
