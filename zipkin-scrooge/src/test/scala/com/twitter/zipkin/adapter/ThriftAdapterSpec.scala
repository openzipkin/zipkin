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

import com.twitter.zipkin.common._
import com.twitter.zipkin.gen
import com.twitter.conversions.time._

import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import java.nio.ByteBuffer

class ThriftAdapterSpec extends Specification with JMocker with ClassMocker {

  "ThriftAdapter" should {
    "convert Annotation" in {
      "to thrift and back" in {
        val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")))
        val thriftAnn: gen.Annotation = ThriftAdapter(expectedAnn)
        val actualAnn: Annotation = ThriftAdapter(thriftAnn)
        expectedAnn mustEqual actualAnn
      }
      "to thrift and back, with duration" in {
        val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")), Some(1.seconds))
        val thriftAnn: gen.Annotation = ThriftAdapter(expectedAnn)
        val actualAnn: Annotation = ThriftAdapter(thriftAnn)
        expectedAnn mustEqual actualAnn
      }
    }

    "convert AnnotationType" in {
      val types = Seq("Bool", "Bytes", "I16", "I32", "I64", "Double", "String")
      "to thrift and back" in {
        types.zipWithIndex.foreach { case (value: String, index: Int) =>
          val expectedAnnType: AnnotationType = AnnotationType(index, value)
          val thriftAnnType: gen.AnnotationType = ThriftAdapter(expectedAnnType)
          val actualAnnType: AnnotationType = ThriftAdapter(thriftAnnType)
          actualAnnType mustEqual expectedAnnType
        }
      }
    }

    "convert BinaryAnnotation" in {
      "to thrift and back" in {
        val expectedAnnType = AnnotationType(3, "I32")
        val expectedHost = Some(Endpoint(123, 456, "service"))
        val expectedBA: BinaryAnnotation =
          BinaryAnnotation("something", ByteBuffer.wrap("else".getBytes), expectedAnnType, expectedHost)
        val thriftBA: gen.BinaryAnnotation = ThriftAdapter(expectedBA)
        val actualBA: BinaryAnnotation = ThriftAdapter(thriftBA)
        actualBA mustEqual expectedBA
      }
    }

    "convert Endpoint" in {
      "to thrift and back" in {
        val expectedEndpoint: Endpoint = Endpoint(123, 456, "service")
        val thriftEndpoint: gen.Endpoint = ThriftAdapter(expectedEndpoint)
        val actualEndpoint: Endpoint = ThriftAdapter(thriftEndpoint)
        expectedEndpoint mustEqual actualEndpoint
      }

      "to thrift and back, with null service" in {
        // TODO this could happen if we deserialize an old style struct
        val actualEndpoint = ThriftAdapter(gen.Endpoint(123, 456, null))
        val expectedEndpoint = Endpoint(123, 456, Endpoint.UnknownServiceName)
        expectedEndpoint mustEqual actualEndpoint
      }
    }

    "convert Span" in {
      val annotationValue = "NONSENSE"
      val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
      val expectedSpan = Span(12345, "methodcall", 666, None,
        List(expectedAnnotation), Nil)

      "to thrift and back" in {
        val thriftSpan: gen.Span = ThriftAdapter(expectedSpan)
        val actualSpan: Span = ThriftAdapter(thriftSpan)
        expectedSpan mustEqual actualSpan
      }

      "handle incomplete thrift span" in {
        val noNameSpan = gen.Span(0, null, 0, None, Seq(), Seq())
        ThriftAdapter(noNameSpan) must throwA[IncompleteTraceDataException]

        val noAnnotationsSpan = gen.Span(0, "name", 0, None, null, Seq())
        ThriftAdapter(noAnnotationsSpan) mustEqual Span(0, "name", 0, None, List(), Seq())

        val noBinaryAnnotationsSpan = gen.Span(0, "name", 0, None, Seq(), null)
        ThriftAdapter(noBinaryAnnotationsSpan) mustEqual Span(0, "name", 0, None, List(), Seq())
      }
    }

    "convert TraceSummary" in {
      "to thrift and back" in {
        val expectedTraceSummary = TraceSummary(123, 10000, 10300, 300, Map("service1" -> 1),
          List(Endpoint(123, 123, "service1")))
        val thriftTraceSummary = ThriftAdapter(expectedTraceSummary)
        val actualTraceSummary = ThriftAdapter(thriftTraceSummary)
        expectedTraceSummary mustEqual actualTraceSummary
      }
    }
  }

}
