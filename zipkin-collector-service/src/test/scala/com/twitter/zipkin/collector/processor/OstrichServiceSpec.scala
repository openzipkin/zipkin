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

import com.twitter.ostrich.stats.{Distribution, Histogram, Stats}
import com.twitter.zipkin.collector.OstrichService
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import com.twitter.zipkin.thriftscala
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class OstrichServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val histogram = Histogram()
  histogram.add(10)
  val distribution = new Distribution(histogram)

  val prefix = "agg."

  before {
    Stats.clearAll()
  }

  test("add two metrics if server span") {
    val agg = new OstrichService(prefix)

    val annotation1 = Annotation(10, thriftscala.Constants.SERVER_RECV, Some(Endpoint(1, 2, "service")))
    val annotation2 = Annotation(20, thriftscala.Constants.SERVER_SEND, Some(Endpoint(3, 4, "service")))
    val annotation3 = Annotation(30, "value3", Some(Endpoint(5, 6, "service")))

    val span = Span(12345, "methodcall", 666, None, List(annotation1, annotation2, annotation3), Nil)

    agg.apply(span)

    Stats.getMetrics()(prefix + "service") should be(distribution)
    Stats.getMetrics()(prefix + "service.methodcall") should be(distribution)
  }

  test("add no metrics since not server span") {
    val agg = new OstrichService(prefix)

    val annotation1 = Annotation(10, thriftscala.Constants.CLIENT_SEND, Some(Endpoint(1, 2, "service")))
    val annotation2 = Annotation(20, thriftscala.Constants.CLIENT_RECV, Some(Endpoint(3, 4, "service")))
    val annotation3 = Annotation(30, "value3", Some(Endpoint(5, 6, "service")))

    val span = Span(12345, "methodcall", 666, None, List(annotation1, annotation2, annotation3), Nil)

    agg.apply(span)

    Stats.getMetrics() should not contain key(prefix + "service")
    Stats.getMetrics() should not contain key(prefix + "service.methodcall")
  }
}
