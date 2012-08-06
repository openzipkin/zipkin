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
package com.twitter.zipkin.adapter

import com.twitter.zipkin.common.{Annotation, Span, Endpoint}
import com.twitter.zipkin.query.{Trace, TraceSummary}
import org.specs.mock.{JMocker, ClassMocker}
import org.specs.Specification

class ThriftQueryAdapterSpec extends Specification with JMocker with ClassMocker {

  "ThriftQueryAdapter" should {

    "convert Trace" in {
      "to thrift and back" in {
        val span = Span(12345, "methodcall", 666, None,
          List(Annotation(1, "boaoo", None)), Nil)
        val expectedTrace = Trace(List[Span](span))
        val thriftTrace = ThriftQueryAdapter(expectedTrace)
        val actualTrace = ThriftQueryAdapter(thriftTrace)
        expectedTrace mustEqual actualTrace
      }
    }

    "convert TraceSummary" in {
      "to thrift and back" in {
        val expectedTraceSummary = TraceSummary(123, 10000, 10300, 300, Map("service1" -> 1),
          List(Endpoint(123, 123, "service1")))
        val thriftTraceSummary = ThriftQueryAdapter(expectedTraceSummary)
        val actualTraceSummary = ThriftQueryAdapter(thriftTraceSummary)
        expectedTraceSummary mustEqual actualTraceSummary
      }
    }
  }
}
