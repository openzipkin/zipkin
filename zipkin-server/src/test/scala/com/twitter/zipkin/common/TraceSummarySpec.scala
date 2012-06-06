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
package com.twitter.zipkin.common

import org.specs.Specification

class TraceSummarySpec extends Specification {
  "TraceSummary" should {
    "convert to thrift and back" in {
      val expectedTraceSummary = TraceSummary(123, 10000, 10300, 300, Map("service1" -> 1),
        List(Endpoint(123, 123, "service1")))
      val thriftTraceSummary = expectedTraceSummary.toThrift
      val actualTraceSummary = TraceSummary.fromThrift(thriftTraceSummary)
      expectedTraceSummary mustEqual actualTraceSummary
    }
  }
}
